package com.keyvanfatehi.DiontForAndroid;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

import java.net.SocketException;
import java.util.HashMap;
import java.util.UUID;

/**
 * Ported from the Cordova plugin and associated JavaScript by keyvan on 8/19/15.
 */
public class Diont {
    private static final String TAG = Diont.class.getSimpleName();

    String MULTICAST_HOST = "224.0.0.236";
    int MULTICAST_PORT = 60540;
    private final String instanceId;
    HashMap<String, MulticastSocket> sockets;
    HashMap<String, SocketListener> listeners;

    public Diont() {
        instanceId = UUID.randomUUID().toString();
        sockets = new HashMap<String, MulticastSocket>();
        listeners = new HashMap<String, SocketListener>();
    }

    public void queryForServices() {
        JSONObject jo = new JSONObject();
        try {
            jo.put("eventType", "query");
            jo.put("fromDiontInstance", instanceId);
            this.send(jo.toString(), MULTICAST_HOST, MULTICAST_PORT);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void init() {
        MulticastSocket socket = getSocket();
        if (socket == null) {
            try {
                socket = new MulticastSocket(MULTICAST_PORT);
                socket.setTimeToLive(10); // Local network
                socket.joinGroup(InetAddress.getByName(MULTICAST_HOST)); // Tell the OS to listen for messages on the specified host and treat them as if they were meant for this host
                Boolean disableLoopback = false;
                socket.setLoopbackMode(disableLoopback);

                sockets.put(instanceId, socket);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void listen(DiontMessageHandlerInterface messageHandler) {
        MulticastSocket socket = getSocket();
        try {
            // Set up listener
            SocketListener listener = new SocketListener(socket, messageHandler);
            listeners.put(instanceId, listener);
            listener.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(String message, String host, int port) {
        MulticastSocket socket = getSocket();
        try {
            byte[] bytes = message.getBytes("UTF-8");
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, InetAddress.getByName(host), port);
            socket.send(packet);
            Log.v(TAG, "Sent DatagramPacket to "+InetAddress.getByName(host).getHostAddress() + ": " + message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        MulticastSocket socket = getSocket();
        if (socket != null) {
            socket.close();
            sockets.remove(instanceId);
            SocketListener listener = listeners.get(instanceId);
            if (listener != null) {
                listener.interrupt();
                listeners.remove(instanceId);
            }
        }
    }

    private MulticastSocket getSocket() {
        return sockets.get(instanceId);
    }

    private class SocketListener extends Thread {

        MulticastSocket socket;
        DiontMessageHandlerInterface handler;

        public SocketListener(MulticastSocket socket, DiontMessageHandlerInterface handler) {
            this.socket = socket;
            this.handler = handler;
        }

        public void run() {
            byte[] data = new byte[2048];
            DatagramPacket packet = new DatagramPacket(data, data.length);
            boolean running = true;
            while (running) {
                try {
                    Log.v(TAG, "Waiting to receive packet");
                    this.socket.receive(packet);
                    String message = new String(data, 0, packet.getLength(), "UTF-8")
                            .replace("'", "\'")
                            .replace("\r", "\\r")
                            .replace("\n", "\\n");
                    this.handler.handleMessage(message);
                } catch (SocketException e) {
                    if (socket.isClosed())
                        running = false;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Log.v(TAG, "SocketListener no longer running");
        }
    }
}
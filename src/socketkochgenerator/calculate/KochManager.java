/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package socketkochgenerator.calculate;

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.nio.channels.FileLock;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.paint.Color;
import socketkochgenerator.CacheManager.CacheManager;
import socketkochgenerator.CacheManager.EdgeLocationBlock;

/**
 *
 * @author rick-
 */
public class KochManager {

    public boolean notActive() {
        return socket.isConnected() && !socket.isClosed();
    }

    public static enum OutputMode {
        DIRECT_WRITE,
        CACHE_WRITE
    }

    public static interface updateCallback {

        public void update(Edge e) throws InterruptedException;
    }

    private int level = 1;
    private KochFractal kf;

    private ExecutorService pool;
    private CountDownLatch lat;

    private Socket socket;
    private DataOutputStream objOut;

    private Thread listenerThread;

    Runnable lt, rt, bt;
    Runnable rEnd;

    public KochManager(Socket s) {

        socket = s;

        //save the data
        kf = new KochFractal(this);
        //create the koch fractal
        kf.setLevel(level);

        //set the value
        changeLevel(level);

        listenerThread = new Thread(() -> socketHandler());
        listenerThread.start();
    }

    private void socketHandler() {
        
        OutputStream out;
        DataInputStream objIn;
        
        
        System.out.println("Writing header!");
        try {
            objOut = new DataOutputStream(socket.getOutputStream());
            objOut.writeUTF("RDES");//identify server
            objOut.flush();
        } catch (IOException ex) {
            Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        try {
            out = socket.getOutputStream();
            objIn = new DataInputStream(socket.getInputStream());
        } catch (IOException ex) {
            Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        System.out.println("Waiting input");
        while (!socket.isClosed() && socket.isConnected()) {
            try {
                int val = objIn.readByte();
                switch (val) {
                    case 0x01://draw edges direct write
                        System.out.println("Writing direct");
                        changeLevel(objIn.readByte());

                        EdgeLocationBlock location = CacheManager.getInstance().getLevelOffset(level);
                        if (location == null) {
                            System.out.println("Calculated");
                            location = CacheManager.getInstance().creaeNewLevel(level);
                            calculate(OutputMode.DIRECT_WRITE, objIn.readDouble(),location);
                        }else{
                            System.out.println("From cache");
                            writeFromCache(location,objIn.readDouble());
                        }
                        System.out.println("Done");
                        break;
                    case 0x02://draw edges chached write
                        System.out.println("Writing Cached");
                        changeLevel(objIn.readByte());
                        
                        EdgeLocationBlock location2 = CacheManager.getInstance().getLevelOffset(level);
                        if (location2 == null) {
                            System.out.println("Calculated");
                            location2 = CacheManager.getInstance().creaeNewLevel(level);
                            calculate(OutputMode.CACHE_WRITE, objIn.readDouble(),location2);
                        }else{
                            System.out.println("From cache");
                            writeFromCache(location2,objIn.readDouble());
                        }
                        System.out.println("Done");
                        break;
                    default:
                        out.write(0xFF);//ERROR
                        System.out.println("Error");
                        break;
                }
            } catch (IOException ex) {
                Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        System.out.println("Quiting");
    }

    public void changeLevel(int value) {
        //set the level
        level = value;
        kf.setLevel(level);
    }

    public void calculate(OutputMode outputMode, double zoom, EdgeLocationBlock location) {
        pool = Executors.newFixedThreadPool(3);
        System.out.println("Generating edges " + kf.getNrOfEdges() + " for level " + kf.getLevel());

        RandomAccessFile cacheFile = CacheManager.getInstance().getWriter();
        
        DataOutputStream socketOutput;
        try {
            cacheFile.seek(location.getStart());
            cacheFile.writeByte(level);

            //socketOutput = new DataOutputStream(socket.getOutputStream());
            socketOutput = objOut;
            if (outputMode == OutputMode.DIRECT_WRITE) {
                socketOutput.writeByte(0x01);//direct write level started caculating
                socketOutput.writeByte(level);//send the level
            } else {
                //write at end
            }
        } catch (IOException ex) {
            Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        DataOutput dout = cacheFile;

        //create 3 threads for the calculations
        lat = new CountDownLatch(3);

        lt = () -> {
            kf.generateLeftEdge(lat, (Edge e) -> {
                synchronized (dout) {
                    try {
                        write(e, dout);
                        if (outputMode == OutputMode.DIRECT_WRITE) {
                            write(edgeAfterZoom(e, zoom), socketOutput);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        };
        rt = () -> {
            kf.generateRightEdge(lat, (Edge e) -> {
                synchronized (dout) {
                    try {
                        write(e, dout);
                        if (outputMode == OutputMode.DIRECT_WRITE) {
                            write(edgeAfterZoom(e, zoom), socketOutput);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        };
        bt = () -> {
            kf.generateBottomEdge(lat, (Edge e) -> {
                synchronized (dout) {
                    try {
                        write(e, dout);
                        if (outputMode == OutputMode.DIRECT_WRITE) {
                            write(edgeAfterZoom(e, zoom), socketOutput);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        };

        pool.execute(bt);
        pool.execute(lt);
        pool.execute(rt);

        try {
            lat.await();

            //write data from cache to outputstream if in cache write mode
            if (outputMode == OutputMode.CACHE_WRITE) {
                writeFromCache(location, zoom);
            }else{
                socketOutput.flush();
            }

            pool.shutdown();
            CacheManager.getInstance().releaseWriter();
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private void writeFromCache(EdgeLocationBlock location, double zoom) throws IOException {
        RandomAccessFile cacheFile = new RandomAccessFile(CacheManager.getInstance().getLocation(),"r");
        DataOutputStream socketOutput = new DataOutputStream(socket.getOutputStream());
        socketOutput.writeByte(0x02);//write from cache
        cacheFile.seek(location.getStart());

        socketOutput.writeByte(cacheFile.read());//write level
        for (int i = 0; i < kf.getNrOfEdges(); i++) {
            write(edgeAfterZoom(cacheFile.readDouble(), cacheFile.readDouble(), cacheFile.readDouble(), cacheFile.readDouble(),//XYXY
                    cacheFile.readDouble(), cacheFile.readDouble(), cacheFile.readDouble(), //hsb
                    zoom), socketOutput);//write all edges
        }
        socketOutput.flush();
    }

    private void write(Edge e, DataOutput dout) throws IOException {
        dout.writeDouble(e.X1);
        dout.writeDouble(e.Y1);
        dout.writeDouble(e.X2);
        dout.writeDouble(e.Y2);
        dout.writeDouble(e.color.getHue());
        dout.writeDouble(e.color.getSaturation());
        dout.writeDouble(e.color.getBrightness());
    }

    public void stop() {
        kf.cancel();
        pool.shutdown();
    }

    private Edge edgeAfterZoom(Edge e, double zoom) {
        return new Edge(
                e.X1 * zoom,
                e.Y1 * zoom,
                e.X2 * zoom,
                e.Y2 * zoom,
                e.color);
    }

    private Edge edgeAfterZoom(double X1, double Y1, double X2, double Y2, double h, double s, double b, double zoom) {
        return new Edge(
                X1 * zoom,
                Y1 * zoom,
                X2 * zoom,
                Y2 * zoom,
                Color.hsb(h, s, b));
    }
}

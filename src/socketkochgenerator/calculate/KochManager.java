/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package calculate;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.Socket;
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
import socketkochgenerator.CacheManager.CacheManager;
import socketkochgenerator.CacheManager.EdgeLocationBlock;
/**
 *
 * @author rick-
 */
public class KochManager{

    public static enum OutputMode{
        DIRECT_WRITE,
        CACHE_WRITE
    }
    
    
    public static interface updateCallback{
        public void update(Edge e) throws InterruptedException;
    }
    
    private int level = 1;
    private KochFractal kf;
    
    private ExecutorService pool;
    private CountDownLatch lat;
    
    private RandomAccessFile cacheFile;
    
    private Socket socket;
    
    private Thread listenerThread;
    
    Runnable lt,rt,bt;
    Runnable rEnd;
    
    public KochManager(Socket s){

        try {
            cacheFile = new RandomAccessFile(CacheManager.getInstance().getLocation(),"r");
        } catch (FileNotFoundException ex) {
            Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        
        socket = s;

        //save the data
        kf = new KochFractal(this);
        //create the koch fractal
        kf.setLevel(level);
        
        //set the value
        changeLevel(level);
        
        listenerThread = new Thread(()->socketHandler());
        listenerThread.start();
    }
    
    private void socketHandler(){
        OutputStream out;
        InputStream in;
        try {
            out = socket.getOutputStream();
            in = socket.getInputStream();
        } catch (IOException ex) {
            Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }
        try {
            out.write("RDES".getBytes());//identify server
        } catch (IOException ex) {
            Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        while(!socket.isClosed()&&socket.isConnected()){
            try {
                int val = in.read();
                switch(val){
                    case 0x01://draw edge
                        OutputMode outputMode = in.read()==1?OutputMode.DIRECT_WRITE:OutputMode.CACHE_WRITE;
                        int zoom = in.read();
                        
                        break;
                }
            } catch (IOException ex) {
                Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
            }
            
        }
    }
    
    public void changeLevel(int value){
        //set the level
        level=value;
        kf.setLevel(level);
    }
    
    public void calculate(OutputMode outputMode){
        pool = Executors.newFixedThreadPool(3);
        System.out.println("Generating edges " + kf.getNrOfEdges() + " for level " + kf.getLevel());
        
        EdgeLocationBlock location = CacheManager.getInstance().getLevelOffset(level);
        if(location == null){
            location = CacheManager.getInstance().creaeNewLevel(level);
        }
        DataOutputStream socketOutput;
        try {
            cacheFile.seek(location.getStart());
            cacheFile.writeByte(level);
        
             socketOutput = new DataOutputStream(socket.getOutputStream());

            if(outputMode == OutputMode.DIRECT_WRITE){
                socketOutput.writeByte(0x01);//direct write level started caculating
                socketOutput.writeByte(level);//send the level
            }else{
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
                synchronized(dout){
                    try {
                        write(e,dout);
                        if(outputMode == OutputMode.DIRECT_WRITE){
                            write(e,socketOutput);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        };
        rt = () -> {
            kf.generateRightEdge(lat, (Edge e) -> {
                synchronized(dout){
                    try {
                        write(e,dout);
                        if(outputMode == OutputMode.DIRECT_WRITE){
                            write(e,socketOutput);
                        }
                    } catch (IOException ex) {
                        Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
        };
        bt = () -> {
            kf.generateBottomEdge(lat, (Edge e) -> {
                synchronized(dout){
                    try {
                        write(e,dout);
                        if(outputMode == OutputMode.DIRECT_WRITE){
                            write(e,socketOutput);
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
            
            //write data from cache to utputstream if in cache write mode
            if(outputMode == OutputMode.CACHE_WRITE){
                socketOutput.writeByte(0x02);//write at end
                cacheFile.seek(location.getStart());
                
                for(int i = 0; i < location.getLength();i++){
                    socketOutput.writeByte(cacheFile.read());
                }
                
            }
            
            pool.shutdown();
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(KochManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void write(Edge e, DataOutput dout) throws IOException{
        dout.writeDouble(e.X1);
        dout.writeDouble(e.Y1);
        dout.writeDouble(e.X2);
        dout.writeDouble(e.Y2);
        dout.writeDouble(e.color.getHue());
        dout.writeDouble(e.color.getSaturation());
        dout.writeDouble(e.color.getBrightness());
    }
    
    public void stop(){
        kf.cancel();
        pool.shutdown();
    }
}

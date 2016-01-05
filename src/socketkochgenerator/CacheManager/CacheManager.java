/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package socketkochgenerator.CacheManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Rick Rongen, www.R-Ware.tk
 */
public class CacheManager {
    private static final Logger LOG = Logger.getLogger(CacheManager.class.getName());
    
    private static CacheManager instance;
    
    public synchronized static CacheManager getInstance(){
        if(instance == null){
            instance = new CacheManager();
        }
        return instance;
    }
    
    private RandomAccessFile file;
    private int firstFreeByte = -1;
    private int levelCount = -1;
    private List<EdgeLocationBlock> edgeLocationBlocks;
    private File location;

    public CacheManager() {
        boolean newFile;
        location = new File(System.getProperty("user.home") + File.separator + "edgesCache.edg");
        newFile = !location.exists();
        try {
            file = new RandomAccessFile(location,"rw");
        } catch (FileNotFoundException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        if(newFile){
            try {
                createTable();
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
        try {
            file.seek(0);
            if(!(file.readChar() == 'R' && file.readChar() == 'D' && file.readChar() == 'E')){//corrupt!
                //todo ask user what to do
                createTable();
            }else{
                firstFreeByte = file.readInt();
                levelCount = (int)file.readByte();
                edgeLocationBlocks = new ArrayList<>();
                for(int i=0; i<levelCount; i++){
                    //read all known edge location blocks
                    edgeLocationBlocks.add(new EdgeLocationBlock((int)file.readByte(),file.readInt(),file.readInt()));
                }
                //remove all invallid blocks
                if(edgeLocationBlocks.removeIf((item)->item.getLevel()<1 || item.getLength()!=calcSize(item.getLevel())||item.getStart()<0x1000)){
                    //important there where bad blocks!
                }
                //todo check the edge structers?
            }
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        
    }
    
    /**
     * formats the edge location table
     * ALL DATA IS LOST
     */
    private void createTable() throws IOException{
        file.seek(0);
        for(int i = 0; i<0x1000; i++){
            file.write(0x00);
        }
        file.seek(0);
        file.writeChar('R');//header
        file.writeChar('D');
        file.writeChar('E');
        file.writeInt(0x1000);//first free byte
        file.write(0x00);//no levels
    }
    
    /**
     * Get the edge location block for the specified level
     * @param level the level
     * @return the edge location block or null if not found
     */
    public synchronized EdgeLocationBlock getLevelOffset(int level){
        Optional<EdgeLocationBlock> result = edgeLocationBlocks.stream().filter((blck)->blck.getLevel() == level).findFirst();
        return result.isPresent()?result.get():null;
    }
    
    /**
     * Create a new level
     * @param level the level to create
     * @return the location of where to put it or null if not possible
     */
    public synchronized EdgeLocationBlock creaeNewLevel(int level){
        if(edgeLocationBlocks.stream().anyMatch((blck)->blck.getLevel() == level)){
            return null;//edge already exists
        }
        //reserve space
        EdgeLocationBlock block = new EdgeLocationBlock(level,firstFreeByte,calcSize(level),false);
        firstFreeByte+=block.getLength();
        
        //todo check space for writing block
        //larger then 0x1000
        
        try {
            //write to file
            file.writeByte((byte)level);
            file.writeInt(block.getStart());
            file.writeInt(block.getLength());
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        
        //register block
        edgeLocationBlocks.add(block);
        
        return block;
    }

    public File getLocation() {
        return location;
    }
    
    private int calcSize(int level){
        return (int)((3*Math.pow(4, level - 1))*56+1);
    }
}

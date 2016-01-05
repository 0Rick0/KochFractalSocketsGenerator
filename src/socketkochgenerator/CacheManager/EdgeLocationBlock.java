/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package socketkochgenerator.CacheManager;

/**
 *
 * @author Rick Rongen, www.R-Ware.tk
 */
public class EdgeLocationBlock {
    private final int level;
    private final int start;
    private final int length;
    private boolean done = true;
    
    public EdgeLocationBlock(int level, int start, int length) {
        this.level = level;
        this.start = start;
        this.length = length;
    }

    public EdgeLocationBlock(int level, int start, int length, boolean done) {
        this.level = level;
        this.start = start;
        this.length = length;
        this.done = done;
    }

    public int getLevel() {
        return level;
    }

    public int getStart() {
        return start;
    }

    public int getLength() {
        return length;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }
}

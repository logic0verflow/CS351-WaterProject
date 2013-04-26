package flow;

import cell.Cell;
import cell.Farm;

/**
 * WaterFlow is a class that computes how water should flow from cell to cell.
 *
 * TODO: Account for plants 
 * TODO: Extra-farm flow
 * 
 * @author Max Ottesen
 */
public class WaterFlow {
	
	private double       timeStep = 1; //seconds
	private Farm         farm;
	private Cell[][][]   grid;
	private Double[][][] change;
	private boolean      ready;
	private double[][][] hydraulicHead;
	private double[][][] percentSaturation;
	private FlowWorker[] workers;
	private int          finishedWorkers;

	public WaterFlow(Farm farm) {
		this.farm = farm;
		this.grid = farm.getGrid();
		this.change = new Double[Farm.SIZE][Farm.SIZE][farm.zCellCount];
		this.ready = false;
		this.finishedWorkers = 0;
		workers = new FlowWorker[4];
		
		workers[0] = new FlowWorker(0, (int)Farm.xCellCount/2, 0, (int)Farm.yCellCount/2, 0, farm.zCellCount, this, grid, change, timeStep);
		workers[1] = new FlowWorker((int)Farm.xCellCount/2, Farm.xCellCount, 0, (int)Farm.yCellCount/2, 0, farm.zCellCount, this, grid, change, timeStep);
		workers[2] = new FlowWorker(0, (int)Farm.xCellCount/2, (int)Farm.yCellCount/2, Farm.yCellCount, 0, farm.zCellCount, this, grid, change, timeStep);
		workers[3] = new FlowWorker((int)Farm.xCellCount/2, Farm.xCellCount, (int)Farm.yCellCount, Farm.yCellCount, 0, farm.zCellCount, this, grid, change, timeStep);
		
		for(int i = 0; i < 4; i++) {
		  workers[i].start();
		}
	}


	/**
	 * Runs the model for a given number of seconds
	 */
	public void update(double seconds) {
		for(double i = 0; i < seconds; i += this.timeStep) {
			this.update();
		}
	}


	/**
	 * Runs the model for one time step
	 */
	private void update() {
	  
	  //Calculate all hydraulic heads
	  //TODO: consider having worker threads do this
	  for(int k = 0; k < farm.getZCellCount(); k++) {
	    for(int j = 0; j < Farm.yCellCount; j++) {
	      for(int i = 0; i < Farm.xCellCount; i++) {
	        if(grid[i][j][k] == null) {
	          hydraulicHead[i][j][k] = new Double(-1);
	        }
	        else {
	          hydraulicHead[i][j][k] = new Double(hydraulicHead(grid[i][j][k]));
	        }
	      }
	    }
	  }
	  
	  //Calculate all percent saturations
	  //TODO: consider having worker threads do this
	  for(int k = 0; k < farm.getZCellCount(); k++) {
	    for(int j = 0; j < Farm.yCellCount; j++) {
	      for(int i = 0; i < Farm.xCellCount; i++) {
	        if(grid[i][j][k] == null) {
	          percentSaturation[i][j][k] = new Double(-1);
	        }
	        else {
	          percentSaturation[i][j][k] = new Double(percentSaturation(grid[i][j][k]));
	        }
	      }
	    }
	  }

	  this.ready = true;
	  for(int i = 0; i < 4; i++) {
	    workers[i].startCalculations();
	  }
	  
	  this.ready = false;
	  
	  while(finishedWorkers != 4) {
	    try{Thread.sleep(1);} 
	    catch (InterruptedException e){}
	  }
	  
	  finishedWorkers = 0;
	  
	   
		
		//Update the amount of water that all the cells have
		for(int k = 0; k < farm.getZCellCount(); k++) {
			for(int j = 0; j < Farm.yCellCount; j++) {
				for(int i = 0; i < Farm.xCellCount; i++) {
					if(grid[i][j][k] == null) {
						continue;
					}
					grid[i][j][k].setWaterVolume(grid[i][j][k].getWaterVolume() + change[i][j][k]);
				}
			}
		}
		
		//Zero out the change holder
		reset(change);
	}


	/**
	 * Computes the hydraulic head of the given cell
	 * @param c - the cell being considered
	 * @return the hydraulic head of the given cell
	 */
	private double hydraulicHead(Cell c) {
		double saturation = percentSaturation(c);
		double height = c.getHeight();
		int x = c.getCoordinate().x;
		int y = c.getCoordinate().y;
		int z = c.getCoordinate().z;
		
		
		//Adds the heights of all the cells above the given cell that are fully saturated
		double heightAbove = 0;
		double s;
		for(int i = 1; i < farm.zCellCount; i++) {
			s = percentSaturation(grid[x][y][z+i]);
			if(s > 99) {
				heightAbove += grid[x][y][z+i].getHeight();
			}
			else {
				break;
			}
		}
		
		//returns the hydraulic head
		return saturation*height + heightAbove;
	}
	
	/**
	 * Computes the percent saturation of the given cell
	 * @param c - the cell being considered
	 * @return the percent saturation of the given cell
	 */
	private double percentSaturation(Cell c) {
		return c.getSoil().getWaterCapacity()/c.getWaterVolume();
	}
	
	
	/**
	 * Sets a Double[][][] array to all 0s
	 * @param array - the array to be reset
	 */
	private void reset(Double[][][] array) {
	   //Reset the change holder
    for(int k = 0; k < farm.getZCellCount(); k++) {
      for(int j = 0; j < Farm.yCellCount; j++) {
        for(int i = 0; i < Farm.xCellCount; i++) {
          array[i][j][k] = new Double(0);
        }
      }
    } 
	}
	
	/**
	 * Returns the percent saturation of a specified cell
	 * @param x - the X-coordinate of the cell
	 * @param y - the Y-coordinate of the cell
	 * @param z - the Z-coordinate of the cell
	 * @return the percent saturation of the cell
	 */
	public double getPercentSaturation(int x, int y, int z) {
	  return percentSaturation[x][y][z];
	}
	
	 /**
   * Returns the hydraulic head of a specified cell
   * @param x - the X-coordinate of the cell
   * @param y - the Y-coordinate of the cell
   * @param z - the Z-coordinate of the cell
   * @return the hydraulic head of the cell
   */
	public double getHydraulicHead(int x, int y, int z) {
	  return hydraulicHead[x][y][z];
	}
	
	/**
	 * Tells worker threads if the master thread (this) is ready for them to start doing their calculations.
	 * This is used to keep the threads from getting ahead or behind
	 * @return whether or not the master is ready for the workers to work
	 */
	public boolean ready() {
	  return ready;
	}
	
	/**
	 * Lets a worker thread tell the master that it's done
	 */
	public synchronized void workerDone() {
	  finishedWorkers++;
	}

}

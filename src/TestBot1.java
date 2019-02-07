import java.util.Vector;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener 
{

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;
    
    private Vector<Unit> selfWorkerList = new Vector<Unit>(0);
    
    private Vector<Unit> selfGasWorker = new Vector<Unit>(0);
    
    private Vector<Unit> selfBuildingList = new Vector<Unit>(0);
    
    boolean areListInitialized = false;
    
    int currentMinerals;
    
    int maxWorkers;
    
    int currentGasRefineries = 0;
    int currentBaseNumber = 0;

    public void run() 
    {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) 
    {
        System.out.println("New unit discovered " + unit.getType());
    }

    @Override
    public void onStart() 
    {
        game = mirror.getGame();
        self = game.self();
        
        game.setLocalSpeed(20);

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations())
        {
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints())
        	{
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }
    }

    @Override
    public void onFrame() 
    {
    	currentMinerals = self.minerals();
    	
    	if(!areListInitialized)
    	{
    		InitListsAndVariables();
    	}
    	
    	ResetCounters();
    	
    	DrawDebugs();
        
        SendWorkersToMine();
        
        if(currentGasRefineries > 0 && selfGasWorker.size()%3 != 0)
        {
        	SendWorkersToGas();
        }
        
        //if we lack SCVs, try to build an SCV but not too much of em.
        if (selfWorkerList.size() < maxWorkers * currentBaseNumber + (currentGasRefineries*3) && currentMinerals >= 50 && self.supplyTotal() - self.supplyUsed() > 6)
        {
            TrainUnit(UnitType.Terran_SCV);
        }
        
        if(selfWorkerList.size() >= 9 && currentGasRefineries < currentBaseNumber)
        {
        	BuildBuilding(selfWorkerList.firstElement(), UnitType.Terran_Refinery);
        }
        
        if(self.supplyTotal() - self.supplyUsed() <= 6)
        {
        	BuildBuilding(selfWorkerList.firstElement(), UnitType.Terran_Supply_Depot);
        }
    }
    
    //Ensures filling of unit & building list.
    @Override
    public void onUnitComplete(Unit unit)
    {
    	if(unit.getType().isWorker() && !selfWorkerList.contains(unit) && unit.getPlayer() == self)
    	{
    		selfWorkerList.add(unit);
    	}
    	if(unit.getType().isBuilding() && !selfBuildingList.contains(unit) && unit.getPlayer() == self)
    	{
    		selfBuildingList.add(unit);
    		if(unit.getType() == UnitType.Terran_Refinery)
    		{
    			++currentGasRefineries;
    		}
    		if(unit.getType() == UnitType.Terran_Command_Center)
    		{
    			++currentBaseNumber;
    		}
    	}
    }
    
    //Ensures dead unit / destroyed buildings are removed from lists.
    @Override
    public void onUnitDestroy(Unit unit)
    {
    	if(unit.getType().isWorker() && selfWorkerList.contains(unit) && unit.getPlayer() == self)
    	{
    		selfWorkerList.remove(unit);
    	}
    	if(unit.getType().isBuilding() && selfBuildingList.contains(unit) && unit.getPlayer() == self)
    	{
    		selfBuildingList.remove(unit);
    	}
    }
    
    //Draw several needed debugs.
    public void DrawDebugs()
    {
        game.drawTextScreen(10, 10,"Workers alive : " + selfWorkerList.capacity());
        game.drawTextScreen(10,230, "Resources: " + self.minerals() + " minerals " + self.gas() + " gas");
    }
    
    //initializes list and variables you need initialized at first frame.
    public void InitListsAndVariables()
    {
    	//if need, use this to initialize any list or variable that wouldn't be initialized using the event onUnitComplete
    	int visibleMineralPatches = 0;
    	for(Unit unit : game.neutral().getUnits())
    	{
    		if(unit.getType() == UnitType.Resource_Mineral_Field)
    		{
    			if(game.isVisible(unit.getTilePosition()))
    			{
    				++visibleMineralPatches;
    			}
    		}
    	}
    	maxWorkers = (int)Math.ceil((double)(visibleMineralPatches*2.5f));
    }
    
    //Returns a valid position to build a building.
    public TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) 
    {
    	TilePosition ret = null;
    	int maxDist = 3;
    	int stopDist = 40;

    	// Refinery, Assimilator, Extractor
    	if (buildingType.isRefinery()) 
    	{
    		for (Unit n : game.neutral().getUnits()) 
    		{
    			if ((n.getType() == UnitType.Resource_Vespene_Geyser) &&
    					( Math.abs(n.getTilePosition().getX() - aroundTile.getX()) < stopDist ) &&
    					( Math.abs(n.getTilePosition().getY() - aroundTile.getY()) < stopDist )
    					) return n.getTilePosition();
    		}
    	}

    	while ((maxDist < stopDist) && (ret == null)) 
    	{
    		for (int i=aroundTile.getX()-maxDist; i<=aroundTile.getX()+maxDist; ++i) 
    		{
    			for (int j=aroundTile.getY()-maxDist; j<=aroundTile.getY()+maxDist; ++j) 
    			{
    				if (game.canBuildHere(new TilePosition(i,j), buildingType, builder, false)) 
    				{
    					// units that are blocking the tile
    					boolean unitsInWay = false;
    					for (Unit u : game.getAllUnits()) 
    					{
    						if (u.getID() == builder.getID()) 
    							continue;
    						if ((Math.abs(u.getTilePosition().getX()-i) < 4) && (Math.abs(u.getTilePosition().getY()-j) < 4)) 
    							unitsInWay = true;
    					}
    					if (!unitsInWay) 
    					{
    						return new TilePosition(i, j);
    					}
    					// creep for Zerg
    					if (buildingType.requiresCreep()) 
    					{
    						boolean creepMissing = false;
    						for (int k=i; k<=i+buildingType.tileWidth(); ++k) 
    						{
    							for (int l=j; l<=j+buildingType.tileHeight(); ++l) 
    							{
    								if (!game.hasCreep(k, l)) creepMissing = true;
    								break;
    							}
    						}
    						if (creepMissing) continue;
    					}
    				}
    			}
    		}
    		maxDist += 2;
    	}

    	if (ret == null) game.printf("Unable to find suitable build position for "+buildingType.toString());
    	return ret;
    }

    public void SendWorkerToMine(Unit myUnit)
    {
    	Unit closestMineral = null;

        //find the closest mineral
        for (Unit neutralUnit : game.neutral().getUnits())
        {
            if (neutralUnit.getType().isMineralField())
            {
                if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral))
                {
                    closestMineral = neutralUnit;
                }
            }
        }

        //if a mineral patch was found, send the worker to gather it
        if (closestMineral != null && myUnit.isIdle() && !myUnit.isGatheringGas()) 
        {
            myUnit.gather(closestMineral, false);
        }
    }
    
    public void SendWorkerToGas(Unit worker)
    {
    	Unit closestGas = null;
    	
    	for (Unit building : selfBuildingList)
    	{
    		if(building.getType() == UnitType.Terran_Refinery)
    		{
    			if(closestGas == null || worker.getDistance(building) < worker.getDistance(closestGas))
    			{
    				closestGas = building;
    			}
    		}
    	}
    	
    	if(closestGas != null && worker.isIdle() && !selfGasWorker.contains(worker))
    	{
    		worker.gather(closestGas);
    		selfGasWorker.add(worker);
    	}
    }
    
    public void SendWorkersToMine()
    {
    	for(Unit worker : selfWorkerList)
    	{
    		if(worker.isIdle() && !selfGasWorker.contains(worker))
    		{
    			SendWorkerToMine(worker);
    		}
    	}
    }
    
    public void SendWorkersToGas()
    {
    	for(Unit worker : selfWorkerList)
    	{
    		if(worker.isIdle())
    		{
    			SendWorkerToGas(worker);
    		}
    	}
    }
    
    public boolean TrainUnit(UnitType type)
    {
    	for(Unit building : selfBuildingList)
    	{
    		if(!building.isTraining())
    		{
    			if(building.canTrain(type))    				
    	    	{
    				if((self.minerals() >= type.mineralPrice() && self.gas() >= type.gasPrice()))
    				{
    					return building.train(type);
    				}
    				else
    				{
    					game.printf("You don't have enough resources");
    					return false;
    				}    	    		
    	    	}
    			else
    			{
    				game.printf("You can't train that here");
    				return false;
    			}
    		}
    		else
    		{
    			game.printf("Building is busy");
    			return false;    			
    		}
    	}    	
    	return false;    	
    }
    
    public void ResetCounters()
    {
    	//Reset any counters.
    }
    
    public boolean BuildBuilding(Unit worker, UnitType type)
    {
    	if(null == worker.getBuildUnit())
    	{
    		if(worker.getType().isWorker()) 
        	{
        		if(worker.canBuild(type))
        			
            	{
        			if (self.minerals() >= type.mineralPrice() && self.gas() >= type.gasPrice())
        			{
        				TilePosition tile = getBuildTile(worker, type,self.getStartLocation());
                		return worker.build(type, tile);
        			}
        			else
        			{
        				game.printf("Can't build. You don't have enough workers");
                		return false;
        			}        			
            	}
            	else
            	{
            		game.printf("Can't build. You can't build that");
            		return false;
            	}
        	}
        	else
        	{
        		game.printf("Unit is not a Worker.");
        		return false;
        	}
    	}
    	return false;
    }
    
    public static void main(String[] args) 
    {
        new TestBot1().run();
    }
}
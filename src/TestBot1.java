import java.util.Vector;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener 
{

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;
    
    private Vector<Unit> workerList = new Vector<Unit>(0);
    
    private Vector<Unit> buildingList = new Vector<Unit>(0);
    
    boolean areListInitialized = false;

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
    	if(!areListInitialized)
    	{
    		InitLists();
    	}
    	
    	ResetCounters();
    	
    	DrawDebugs();
        
        SendWorkersToMine();
       
        //if we lack SCVs, try to build an SCV
        if (workerList.size() < 9)
        {
            TrainUnit(UnitType.Terran_SCV);
        }
        
        if(self.supplyTotal() - self.supplyUsed() <= 6)
        {
        	BuildBuilding(workerList.firstElement(), UnitType.Terran_Supply_Depot);
        }
    }
    
    @Override
    public void onUnitComplete(Unit unit)
    {
    	if(unit.getType().isWorker() && !workerList.contains(unit))
    	{
    		workerList.add(unit);
    	}
    	if(unit.getType().isBuilding() && !buildingList.contains(unit))
    	{
    		buildingList.add(unit);
    	}
    }
    
    @Override
    public void onUnitDestroy(Unit unit)
    {
    	if(unit.getType().isWorker() && workerList.contains(unit))
    	{
    		workerList.remove(unit);
    	}
    	if(unit.getType().isBuilding() && buildingList.contains(unit))
    	{
    		buildingList.remove(unit);
    	}
    }
    
    public void DrawDebugs()
    {
        //game.drawTextScreen(10, 10,"Workers alive : " + workerList.capacity());
        game.drawTextScreen(10,230, "Resources: " + self.minerals() + " minerals " + self.gas() + " gas");
        for(int i = 0; i < workerList.size(); i++)
        {
        	game.drawTextScreen(10, i*10, workerList.get(i).getType() + " ID : " + i);
        }
    }
    
    public void InitLists()
    {
    	//if need, use this to initialize any list that wouldn't be initialized using the event onUnitComplete
    }
    
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
    		for (int i=aroundTile.getX()-maxDist; i<=aroundTile.getX()+maxDist; i++) 
    		{
    			for (int j=aroundTile.getY()-maxDist; j<=aroundTile.getY()+maxDist; j++) 
    			{
    				if (game.canBuildHere(new TilePosition(i,j), buildingType, builder, false)) 
    				{
    					// units that are blocking the tile
    					boolean unitsInWay = false;
    					for (Unit u : game.getAllUnits()) 
    					{
    						if (u.getID() == builder.getID()) continue;
    						if ((Math.abs(u.getTilePosition().getX()-i) < 4) && (Math.abs(u.getTilePosition().getY()-j) < 4)) unitsInWay = true;
    					}
    					if (!unitsInWay) {
    						return new TilePosition(i, j);
    					}
    					// creep for Zerg
    					if (buildingType.requiresCreep()) 
    					{
    						boolean creepMissing = false;
    						for (int k=i; k<=i+buildingType.tileWidth(); k++) 
    						{
    							for (int l=j; l<=j+buildingType.tileHeight(); l++) 
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
        if (closestMineral != null) 
        {
            myUnit.gather(closestMineral, false);
        }
    }
    
    public void SendWorkersToMine()
    {
    	for(Unit worker : workerList)
    	{
    		if(worker.isIdle())
    		{
    			SendWorkerToMine(worker);
    		}
    	}
    }
    
    public boolean TrainUnit(UnitType type)
    {
    	for(Unit building : buildingList)
    	{
    		if(!building.isTraining())
    		{
    			if(building.canTrain(type) && self.minerals() >= type.mineralPrice() && self.gas() >= type.gasPrice())
    	    	{
    	    		return building.train(type);
    	    	}
    		}
    		else
    		{
    			return false;
    		}
    	}
    	game.printf("Either can't train this unit, or you don't have enough resources");
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
        		if(worker.canBuild(type) && self.minerals() >= type.mineralPrice() && self.gas() >= type.gasPrice())
            	{
        			TilePosition tile = getBuildTile(worker, type,self.getStartLocation());
            		return worker.build(type, tile);
            	}
            	else
            	{
            		game.printf("Can't build. Either you don't have enought resources, or you can't build that");
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
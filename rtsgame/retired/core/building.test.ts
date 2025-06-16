import { Building } from './building.js';
import { Unit } from './unit.js'; // Needed because buildings create Units
import { BUILDING_TYPES } from '../config/buildingTypes.js';
import { UNIT_TYPES } from '../config/unitTypes.js';
import { TERRAIN_TYPES, TILE_SIZE, GRID_SIZE } from '../config/gameConstants.js';

// Mock Implementations (similar to unit.test.js)

const getMockGameState = () => ({
  gameTime: 0,
  paused: false,
  winner: null,
  events: [],
  fpvMode: false,
  resources: {
    blue: { mass: 1000, energy: 1000, massIncome: 0, energyIncome: 0 },
    red: { mass: 1000, energy: 1000, massIncome: 0, energyIncome: 0 },
  },
  addEvent: jest.fn(),
  updateResources: jest.fn(function(team, massDelta, energyDelta) {
    if (this.resources[team]) {
      this.resources[team].mass += massDelta;
      this.resources[team].energy += energyDelta;
    }
  }),
});

const getMockEntityManager = () => ({
  units: [],
  buildings: [],
  effects: [],
  captions: [],
  projectiles: [],
  addUnit: jest.fn(function(unit) { this.units.push(unit); }),
  addBuilding: jest.fn(function(building) { this.buildings.push(building); }),
  addEffect: jest.fn(),
  addCaption: jest.fn(function(caption) { this.captions.push(caption); }), // Captions are used in building update
  addProjectile: jest.fn(),
});

const getMockSeedRandom = () => ({
  init: jest.fn(),
  random: jest.fn(() => 0.5), // Consistent value
});

const getMockTerrain = (gridSize = 100, tileSide = 32, defaultType = TERRAIN_TYPES.LAND) => {
  const terrain = [];
  for (let x = 0; x < gridSize; x++) {
    terrain[x] = [];
    for (let y = 0; y < gridSize; y++) {
      terrain[x][y] = defaultType; // Direct terrain type, as accessed by building.js
    }
  }
  return terrain;
};

const getMockSimulation = () => {
  const mockGameState = getMockGameState();
  const mockEntityManager = getMockEntityManager();
  const mockSeedRandom = getMockSeedRandom();
  const mockTerrain = getMockTerrain(GRID_SIZE, TILE_SIZE);

  return {
    gameState: mockGameState,
    entityManager: mockEntityManager,
    seedRandom: mockSeedRandom,
    terrain: mockTerrain, // Building.setRallyPoint accesses simulation.terrain
    gameContext: { // Pass through for consistency, though Building doesn't use gameContext as much directly
      seedRandom: mockSeedRandom,
      terrain: mockTerrain,
    },
    // Building constructor and update take 'simulation' as argument
    // Unit constructor (called by building) also takes 'simulation'
  };
};

// Default Building Type for tests
const baseBuildingConfig = {
  name: 'TestBuilding',
  maxHp: 500,
  size: 30, // world units
  color: '#000',
  cost: { mass: 100, energy: 100 },
  buildTime: 20, // seconds
  produces: [],
  resourceGeneration: null,
};

// Default Unit Type for production tests
const producibleUnitConfig = {
    name: 'TestProducibleUnit',
    maxHp: 50,
    speed: 5,
    cost: { mass: 25, energy: 25 },
    buildTime: 5, // seconds for unit production by factory
    // ... other necessary unit props
};

const cloneDeep = (obj) => JSON.parse(JSON.stringify(obj));

describe('Building', () => {
  let mockSim;
  let testBuildingType;
  let testProducibleUnitType;

  beforeEach(() => {
    mockSim = getMockSimulation();
    testBuildingType = cloneDeep(baseBuildingConfig);
    testProducibleUnitType = cloneDeep(producibleUnitConfig);

    // Ensure BUILDING_TYPES and UNIT_TYPES are populated for tests
    BUILDING_TYPES.testBuilding = testBuildingType;
    UNIT_TYPES.testUnit = testProducibleUnitType; // For production

    // For resource generation test
    BUILDING_TYPES.massExtractor = {
        ...baseBuildingConfig,
        name: 'Mass Extractor',
        resourceGeneration: { type: 'mass', amount: 1 } // 1 mass per some interval (original code implies per update tick, not per second)
                                                        // The building.js code: resources[this.team][this.type.resourceGeneration.type] += this.type.resourceGeneration.amount;
                                                        // And income is amount * 60. This suggests amount is per tick, and income is per second if 60 ticks/sec.
                                                        // Let's assume 'amount' is per-update call for simplicity of testing.
    };
    BUILDING_TYPES.energyPlant = {
        ...baseBuildingConfig,
        name: 'Energy Plant',
        resourceGeneration: { type: 'energy', amount: 10 }
    };

    // For production test
    BUILDING_TYPES.factory = {
        ...baseBuildingConfig,
        name: 'Factory',
        produces: [UNIT_TYPES.testUnit] // Factory can produce testUnit
    };

    // Clear mocks
    mockSim.gameState.addEvent.mockClear();
    mockSim.entityManager.addUnit.mockClear();
    mockSim.entityManager.addCaption.mockClear();
    mockSim.seedRandom.random.mockClear();

  });

  describe('Constructor', () => {
    it('should initialize basic properties', () => {
      const building = new Building(100, 150, 'blue', BUILDING_TYPES.testBuilding, mockSim);
      expect(building.x).toBe(100);
      expect(building.y).toBe(150);
      expect(building.team).toBe('blue');
      expect(building.type).toEqual(BUILDING_TYPES.testBuilding);
      expect(building.hp).toBe(BUILDING_TYPES.testBuilding.maxHp);
      expect(building.productionQueue).toEqual([]);
      expect(building.productionProgress).toBe(0);
      // Rally point is set by setRallyPoint, which is called in constructor
      // Default rally point is near building if suitable land found
      expect(building.rallyx).toBeDefined();
      expect(building.rallyy).toBeDefined();
    });
  });

  describe('setRallyPoint', () => {
    // Assuming TILE_SIZE = 32, GRID_SIZE = 100
    // Building at (160,160) which is tile (5,5)
    it('should set rally point to a valid land position near the building', () => {
      // Make tile (8,5) (160+100 / 32 approx) LAND
      // Rally positions are checked: x+100, x-100, y+100, y-100 etc.
      // (160+100, 160) = (260, 160). Tile floor(260/32)=8, floor(160/32)=5. So tile (8,5).
      mockSim.terrain[Math.floor((160+100)/TILE_SIZE)][Math.floor(160/TILE_SIZE)] = TERRAIN_TYPES.LAND;
      const building = new Building(160, 160, 'blue', BUILDING_TYPES.testBuilding, mockSim);
      expect(building.rallyx).toBe(160 + 100);
      expect(building.rallyy).toBe(160);
    });

    it('should fallback to building position if no suitable land rally point is found', () => {
      // Make all surrounding tiles water for simplicity
      for(let i = -4; i <= 4; i++) {
          for(let j = -4; j <= 4; j++) {
              const xTile = Math.floor(160/TILE_SIZE) + i;
              const yTile = Math.floor(160/TILE_SIZE) + j;
              if (xTile >=0 && xTile < GRID_SIZE && yTile >=0 && yTile < GRID_SIZE) {
                mockSim.terrain[xTile][yTile] = TERRAIN_TYPES.WATER;
              }
          }
      }
      const building = new Building(160, 160, 'blue', BUILDING_TYPES.testBuilding, mockSim);
      expect(building.rallyx).toBe(160);
      expect(building.rallyy).toBe(160);
    });
  });

  describe('Update: Resource Generation', () => {
    it('should generate mass if it is a mass extractor', () => {
      const extractor = new Building(100,100, 'blue', BUILDING_TYPES.massExtractor, mockSim);
      const initialMass = mockSim.gameState.resources.blue.mass;
      const generationAmount = BUILDING_TYPES.massExtractor.resourceGeneration.amount;

      extractor.update(mockSim, 0.1); // deltaTime not directly used for this part of resource gen

      expect(mockSim.gameState.resources.blue.mass).toBe(initialMass + generationAmount);
      // Check income calculation (amount * 60)
      expect(mockSim.gameState.resources.blue.massIncome).toBe(generationAmount * 60);
    });

    it('should generate energy if it is an energy plant', () => {
      const plant = new Building(100,100, 'red', BUILDING_TYPES.energyPlant, mockSim);
      const initialEnergy = mockSim.gameState.resources.red.energy;
      const generationAmount = BUILDING_TYPES.energyPlant.resourceGeneration.amount;

      plant.update(mockSim, 0.1);

      expect(mockSim.gameState.resources.red.energy).toBe(initialEnergy + generationAmount);
      expect(mockSim.gameState.resources.red.energyIncome).toBe(generationAmount * 60);
    });
  });

  describe('Update: Production', () => {
    let factory;
    const unitToProduce = UNIT_TYPES.testUnit; // Has cost {mass:25, energy:25}, buildTime: 5s

    beforeEach(() => {
      factory = new Building(100,100,'blue', BUILDING_TYPES.factory, mockSim);
      mockSim.gameState.resources.blue = { mass: 100, energy: 100, massIncome:0, energyIncome:0 }; // Sufficient resources
      mockSim.seedRandom.random.mockReturnValue(0.01); // Ensure auto-production queueing condition (random < 0.02) is met
    });

    it('should add a unit to production queue if resources are available (auto-production)', () => {
      factory.update(mockSim, 0.1); // deltaTime for update cycle
      expect(factory.productionQueue.length).toBe(1);
      expect(factory.productionQueue[0]).toEqual(unitToProduce);
      // Check if resources were deducted
      expect(mockSim.gameState.resources.blue.mass).toBe(100 - unitToProduce.cost.mass);
      expect(mockSim.gameState.resources.blue.energy).toBe(100 - unitToProduce.cost.energy);
    });

    it('should not add to queue if resources are insufficient', () => {
      mockSim.gameState.resources.blue = { mass: 10, energy: 10 }; // Insufficient
      factory.update(mockSim, 0.1);
      expect(factory.productionQueue.length).toBe(0);
    });

    it('should progress production and spawn unit when complete', () => {
      // Manually add to queue to bypass auto-queue randomness for this test part
      factory.productionQueue.push(unitToProduce);
      mockSim.gameState.resources.blue.mass -= unitToProduce.cost.mass; // Assume already deducted
      mockSim.gameState.resources.blue.energy -= unitToProduce.cost.energy;

      let elapsedTime = 0;
      const unitBuildTime = unitToProduce.buildTime; // 5 seconds

      // Simulate time passing in increments
      while(elapsedTime < unitBuildTime) {
          const dt = 0.1;
          factory.update(mockSim, dt);
          elapsedTime += dt;
          if (factory.productionQueue.length === 0) break; // Unit produced
      }

      expect(factory.productionQueue.length).toBe(0); // Queue should be empty
      expect(factory.productionProgress).toBe(0); // Progress reset
      expect(mockSim.entityManager.addUnit).toHaveBeenCalledTimes(1);
      expect(mockSim.entityManager.addUnit).toHaveBeenCalledWith(expect.any(Unit)); // Check instance of Unit
      expect(mockSim.entityManager.addUnit.mock.calls[0][0].type).toEqual(unitToProduce);
      expect(mockSim.entityManager.addUnit.mock.calls[0][0].team).toBe('blue');
      expect(mockSim.entityManager.addUnit.mock.calls[0][0].x).toBe(factory.rallyx); // Spawned at rally point

      // Check for caption "Unit ready!"
      expect(mockSim.entityManager.addCaption).toHaveBeenCalledWith(
          expect.objectContaining({ text: `${unitToProduce.name} ready!`})
      );
    });

    it('should show production progress caption', () => {
        factory.productionQueue.push(unitToProduce);
        mockSim.seedRandom.random.mockReturnValue(0.005); // Ensure caption condition (random < 0.01)
        factory.captionCooldown = 0; // Ensure cooldown is not blocking

        factory.update(mockSim, 1.0); // Progress 1 second out of 5
        const progressPercent = Math.floor((1.0 / unitToProduce.buildTime) * 100);

        expect(mockSim.entityManager.addCaption).toHaveBeenCalledWith(
            expect.objectContaining({ text: `Building ${unitToProduce.name} ${progressPercent}%` })
        );
        expect(factory.captionCooldown).toBe(1.5); // Cooldown set
    });

    it('should not auto-queue if produces list is empty or undefined', () => {
        BUILDING_TYPES.testBuildingNoProduce = { ...testBuildingType, produces: [] };
        const buildingNoProduce = new Building(100,100,'blue', BUILDING_TYPES.testBuildingNoProduce, mockSim);
        buildingNoProduce.update(mockSim, 0.1);
        expect(buildingNoProduce.productionQueue.length).toBe(0);

        BUILDING_TYPES.testBuildingNoProduce2 = { ...testBuildingType, produces: undefined };
        const buildingNoProduce2 = new Building(100,100,'blue', BUILDING_TYPES.testBuildingNoProduce2, mockSim);
        buildingNoProduce2.update(mockSim, 0.1);
        expect(buildingNoProduce2.productionQueue.length).toBe(0);
    });

  });
});

// Globals for Jest if not using module imports for these constants
if (typeof BUILDING_TYPES === 'undefined') {
  global.BUILDING_TYPES = {};
}
if (typeof UNIT_TYPES === 'undefined') {
  global.UNIT_TYPES = {};
}
// TERRAIN_TYPES, TILE_SIZE, GRID_SIZE are imported by building.js

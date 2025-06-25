Okay, I've reviewed the entire document and focused on correcting potential markdown formatting issues, especially with tables and code blocks, which are common sources of rendering problems. The primary fix involves removing inconsistent or excessive leading whitespace that can break parsing for these elements.

Here's the corrected version:

---

**Global Game Design Specification (Project: [Your Game's Name Here - e.g., "Computronium Wars"])**

**Version:** 0.1 (Conceptual & Early Mechanics)
**Date:** June 5, 2025

**Table of Contents:**

1.  **Game Vision & Core Pillars**
2.  **Universe & Setting**
    *   2.1. Backstory & Current State
    *   2.2. Environmental Characteristics
3.  **Core Gameplay Loop**
4.  **Resource System**
    *   4.1. Resource Types & Acquisition Table
    *   4.2. Landscape Consumption & Mineralogy
    *   4.3. Remnant Civilizations & "Population" Resource
5.  **Faction Design Principles** (Placeholders for specific factions)
6.  **Unit Design & Capabilities**
    *   6.1. Unit Archetypes & Roles
    *   6.2. Unit Statistics Table (Example Structure)
    *   6.3. Alloys & Material Science
        *   6.3.1. Alloy Properties Table (Example)
        *   6.3.2. Alloy Manufacturing Mechanics
    *   6.4. Computronium Cores & "Dining Philosophers" Resource Allocation
        *   6.4.1. Core States / "Wisemen" Priority Table
        *   6.4.2. "Fork" Allocation Logic (Conceptual)
7.  **Combat Systems**
    *   7.1. Damage Types & Effects Table
    *   7.2. Weapon Systems & Capacitance
        *   7.2.1. Weapon Archetype Table (Example)
    *   7.3. Shield Mechanics
    *   7.4. Armor Mechanics
8.  **Command & Control (C&C)**
    *   8.1. Hierarchical Command & Rank Bonuses
    *   8.2. Latency, Prediction, & "Light Seconds"
    *   8.3. Squad AI & Contextual Behavior
9.  **Proof-of-Work (PoW) & Computational Warfare**
    *   9.1. Defensive PoW (Internal C&C)
    *   9.2. Offensive PoW (Breach Tools, CSA)
    *   9.3. PoW Generation & Consumption Mechanics
10. **Data Architecture & Determinism ("TrikeShed")**
    *   10.1. Deterministic Simulation Principles
    *   10.2. Replay System & "Free Tick" AI
    *   10.3. Idempotent Data & Immutability
11. **Decentralized Meta-Network (Community & "Dogfooding")**
    *   11.1. Kademlia/IPFS-Inspired Architecture
    *   11.2. PoW for Meta-Network Integrity & Offense
12. **Technology Tree & Research**
    *   12.1. Research Categories (Conceptual)
    *   12.2. Tech Unlocks & Dependencies
13. **User Interface (UI) & User Experience (UX) Considerations**
14. **Future Expansion / DLC Hooks** (Placeholder)

## 1. Game Vision & Core Pillars

*   **Vision:** An RTS set in a desolate, resource-scarce dystopian future, where factions engage in sophisticated physical and computational warfare, driven by the need for incomplete knowledge and limited resources. Emphasizes strategic depth, realistic consequences of technological advancement, and emergent gameplay from complex interacting systems.
*   **Core Pillars:**
    *   **Deep Strategic Choice:** Meaningful decisions with lasting consequences.
    *   **Computational Warfare:** Information, C&C, and processing power as key battlegrounds.
    *   **Tangible Technology:** Advanced tech has realistic costs, limitations, and physical presence.
    *   **Dynamic World:** Terrain is a consumable resource; actions visibly change the environment.
    *   **Deterministic & Verifiable:** Robust simulation for fair play, deep analysis, and community trust.
    *   **Emergent Meta-Game:** Game systems designed to foster a rich player-driven ecosystem.

## 2. Universe & Setting

*   **2.1. Backstory & Current State:** A future where humanity, or its post-human descendants, have pushed technological boundaries (Computronium, dimensional travel, programmable matter) but have also exhausted many conventional resources, leading to widespread conflict. Factions are desperate, driven by corporate greed, survivalist ideologies, or AI directives. Remnants of prior, "cancelled" civilizations dot the landscape.
*   **2.2. Environmental Characteristics:**
    *   **Desolate Landscapes:** Varied biomes (barren deserts, frozen wastes, toxic swamps, shattered urban ruins, orbital platforms, dimensional rifts).
    *   **Resource Scarcity:** While raw landscape matter is abundant, specific valuable minerals are localized and hidden.
    *   **Dimensional Instability:** Certain regions may experience fluctuating C&C latencies, sensor interference, or unpredictable environmental effects.

## 3. Core Gameplay Loop

1.  **Survey & Discover:** Use PoW-gated "Detector Widgets" to map mineral concentrations in terrain. Scout for enemy presence and remnant locations.
2.  **Secure & Expand:** Establish outposts, claim mineral-rich territories, extend C&C network coverage.
3.  **Quarry & Refine:** Deploy Quarry Machines to "eat the terrain," extracting generic mass and specific minerals. Process these into alloys, Computronium, and Energy.
4.  **Manufacture & Research:** Build factories, produce units, research new technologies (alloys, weapons, Computronium grades, PoW techniques).
5.  **Command & Conquer:** Engage enemies using strategic C&C (accounting for latency), physical combat (diverse damage types), and computational warfare (PoW attacks, C&C disruption, CSA).
6.  **Adapt & Evolve:** Analyze replays (TrikeShed), adapt strategies, contribute to/exploit the meta-network.

## 4. Resource System

*   **4.1. Resource Types & Acquisition Table**

| Resource Name          | Tier | Primary Source                                     | Secondary Source(s)                      | Primary Uses                                                              | Storage Type        |
| :--------------------- | :--- | :------------------------------------------------- | :--------------------------------------- | :------------------------------------------------------------------------ | :------------------ |
| **Raw Landscape Matter** | -1   | Terrain itself                                     | N/A                                      | Processed into Mass, specific Minerals, or directly into Energy (inefficient) | N/A (Environmental) |
| **Generic Mass**       | 0    | Matter Converters (from Raw Landscape)             | Quarry Machines (byproduct)              | Basic structures, unit chassis, precursor to Energy/Alloys/Computronium   | Faction Global Pool |
| **Specific Minerals**  | 0    | Quarry Machines (from identified rich terrain zones) | Salvage from Remnant Tech                | Advanced Alloys, Computronium synthesis, specialized components           | Faction Global Pool |
| *(e.g., Ferrite)*      |      |                                                    |                                          |                                                                           |                     |
| *(e.g., Crylithium)*   |      |                                                    |                                          |                                                                           |                     |
| **Energy**             | 0    | Conversion Reactors (from Mass/Minerals/Landscape) | Geothermal Vents, Exotic Energy Nodes    | Powers all operations, weapons, shields, Computronium, PoW                | Faction Global Pool |
| **Computronium**       | 1    | Computronium Synthesis Plants (Minerals + Energy)  | Salvage from high-tier Remnant Tech      | AI cores, C&C nodes, PoW arrays, Qubit precursors, exotic matter conversion | Faction Global Pool |
| **Specialized Alloys** | 1    | Alloy Foundries (Minerals + Mass + Energy)         | N/A                                      | Unit/structure construction, component upgrades with specific properties  | Faction Global Pool |
| **Battery Charge**     | 2    | Unit/Structure onboard capacitor/battery           | Recharge from grid/pads, onboard generator | Powers high-drain local abilities, weapons, shields                       | Unit Local          |
| **Computational Cycles** | 2    | Active Computronium Cores                          | N/A                                      | AI, C&C, PoW, Research, Abilities                                         | Faction/Unit Local  |
| **Information (Intel)**| 2    | Sensors, Recon, Spies, Meta-Network              | Data Caches in Ruins                     | Strategic planning, targeting, counter-intelligence                       | Faction Internal DB |
| **Population (Remnant)**| N/A | Captured Remnant Enclaves                          | N/A                                      | Slow trickle of Mass, unique cognitive resource, research subjects        | Enclave Specific    |

*   **4.2. Landscape Consumption & Mineralogy Mechanics:**
    *   Terrain tiles have a `RawMaterialComposition` map (e.g., `{ genericMatter: 0.7, ferrite: 0.15, crylithium: 0.001 }`).
    *   PoW-gated "Detector Widgets" (unit/structure component) scan tiles to reveal `RawMaterialComposition` to the faction. Scan resolution/cost scales with Computronium/PoW.
    *   "Quarry Machines" target specific minerals. Extraction depletes `RawMaterialComposition` and `TerrainTile.DepletionLevel`, visibly altering terrain mesh.
    *   `Factory.ProductionRate` linked to `QuarryMachine.OutputRate`. High production consumes landscape rapidly.
*   **4.3. Remnant Civilizations & "Population" Resource:**
    *   Ruins are map points of interest. Can be salvaged for tech/materials.
    *   If "Pre-Cancelled Populations" exist, capturing their enclave allows for a "Sims-like" minigame: provide basic needs (Energy, shelter) for stability.
    *   Stable populations yield `RemnantPopulation.Productivity` (e.g., small Mass income, unique "Cognitive Units" for low-grade AI research).

---

## 5. Faction Design Principles (Placeholders for specific factions)

*(Content for Faction Design Principles would go here, detailing unique aspects of each playable faction.)*

---

## 6. Unit Design & Capabilities

(Skipping Factions for brevity, assuming multiple distinct ones)

*   **6.1. Unit Archetypes & Roles:** (Standard RTS roles + specialized E-War, C&C, PoW units)
    *   Commander (ACU), Scouts, Assault Infantry/Vehicles, Tanks, Artillery, Anti-Air, Air Units (Fighters, Bombers), Naval Units (if applicable), Engineers/Constructors, Harvesters (Quarry Machines), Sensor Units, E-Warfare Specialists, C&C Relay Units, Dedicated PoW Generation Units/Structures.
*   **6.2. Unit Statistics Table (Example Structure for `UnitTypeData`)**

| Statistic Name               | Data Type      | Description                                                                | Example (Light Scout Drone) | Example (Heavy Tank) |
| :--------------------------- | :------------- | :------------------------------------------------------------------------- | :-------------------------- | :------------------- |
| `id`                         | String         | Unique type identifier                                                     | `scout_drone_t1`            | `heavy_tank_t2`      |
| `displayName`                | String         | In-game name                                                               | "Wasp Scout Drone"          | "Aegis Heavy Tank"   |
| `cost`                       | `Map<Resource, Int>` | Mass, Energy, specific Alloys, Computronium to build                   | `{mass:50, energy:200}`     | `{mass:400, energy:1000, aegisSteel:50}` |
| `buildTime_seconds`          | Float          | Base time to construct                                                       | 5.0                         | 30.0                 |
| `maxHP`                      | Integer        | Maximum health points                                                      | 75                          | 1500                 |
| `armorType`                  | Enum           | (e.g., Light, Medium, Heavy, EnergyResistant)                            | Light                       | Heavy_AegisSteel     |
| `armorValue`                 | Integer        | Base damage reduction value                                                | 5                           | 50                   |
| `maxSpeed_mps`               | Float          | Maximum movement speed                                                     | 20.0                        | 5.0                  |
| `acceleration`               | Float          | Rate of speed change                                                       | 5.0                         | 1.0                  |
| `turnRate_dps`               | Float          | Degrees per second rotation speed                                          | 180                         | 45                   |
| `sensorRange_meters`         | Integer        | Range at which it detects enemies                                          | 500                         | 300                  |
| `visionRange_meters`         | Integer        | Range at which it clears FoW                                                 | 600                         | 350                  |
| `weapons`                    | `Array<WeaponSlot>` | See Weapon Systems Table                                                   | `[{weaponId:"laser_pulse_light", ...}]` | `[{weaponId:"cannon_heavy_kinetic", ...}, {weaponId:"mg_point_defense", ...}]` |
| `maxBatteryCapacity`         | Integer        | Onboard energy storage                                                     | 100                         | 500                  |
| `batteryRechargeRate_ps`     | Integer        | Rate of passive recharge                                                   | 5                           | 10                   |
| `computroniumCoreLevel`      | Integer (1-5)  | Determines "forks," AI sophistication, PoW capacity                      | 1                           | 3                    |
| `defaultCoreFocusMode`       | Enum           | (Offensive, Defensive, C&C, Utility, Balanced)                           | C&C                         | Offensive            |
| `specialAbilities`           | `Array<AbilityID>` | List of activatable abilities                                              | `["sensor_ping", "evasive_maneuvers"]` | `["siege_mode", "emergency_shields"]` |
| `cargoCapacity` (if any)     | Integer        | For transport units                                                        | 0                           | 0                    |
| `constructionOptions` (if any) | `Array<UnitTypeID>` | For constructor units                                                    | N/A                         | N/A                  |

*   **6.3. Alloys & Material Science**
    *   **6.3.1. Alloy Properties Table (Example `AlloyData`)**

| Alloy Name             | Precursor Minerals                          | Base Cost (Mass, Energy) | Key Property Modifiers (+Armor, -Speed, +EnergyDmg, etc.)       | Tech Tier Req. |
| :--------------------- | :------------------------------------------ | :----------------------- | :------------------------------------------------------------ | :------------- |
| `StandardPlasteel`     | Generic Mass                                | Low                      | Base stats                                                    | 0              |
| `AegisSteel`           | Ferrite Composite, Adamantium Flakes        | High                     | +++Armor, +HP, --Speed                                        | 2              |
| `QuicksilverWeave`     | Aerogel Filaments, Silicate Fibers          | Medium                   | +++Speed, ++Agility, --Armor                                  | 2              |
| `FluxResonanceComposite` | Crylithium Crystals, Conductive Polymers    | Very High                | +EnergyWpnDmg, +ShieldStr, +BatteryCap, +ComputroniumCoreEff  | 3              |
| `NocturneAlloy`        | VoidShale Particles, Obsidian Dust          | High                     | -SensorSignature, Enables Cloak                               | 3              |
| `ThermoRegulantCeramic`| Volcanic Glass Shards, Refractory Clays     | Medium                   | +HeatResist, -WeaponOverheat, +EnginePerf                     | 1              |

    *   **6.3.2. Alloy Manufacturing:** Research unlocks blueprint. Alloy Foundry consumes listed precursors + Energy. Output is `Faction.Resources.AlloyNameAmount`. Units requiring alloy deduct from this pool during construction.
*   **6.4. Computronium Cores & "Dining Philosophers" Resource Allocation**
    *   Each unit with a `computroniumCoreLevel > 0` has a core.
    *   `CoreLevel` determines number of "Computational Forks" (e.g., Level 1 = 2 forks, Level 5 = 5 forks, Qubit Core = effectively many more).
    *   Unit functions (weapons, shields, movement, C&C, PoW, abilities) are "Philosophers" requiring 1+ "Forks" to operate optimally.
    *   **6.4.1. Core States / "Wisemen" Priority Table (Conceptual)**

| Core Focus Mode   | Primary Fork Allocation Bias                                | Secondary Allocation                          | Penalty Example                                      |
| :---------------- | :---------------------------------------------------------- | :-------------------------------------------- | :--------------------------------------------------- |
| **Offensive (Mars)**| Weapon Systems, Targeting AI (multi-target)                 | Short-range sensors, basic movement           | PoW contribution reduced, C&C update latency increased |
| **Defensive (Juno)**| Shield Management, Point Defense, Damage Control, Evasion   | Basic weapon use, threat assessment           | Offensive RoF reduced, PoW contribution minimal      |
| **C&C (Mercury)**   | C&C Uplink/Downlink, PoW Contribution, Long-Range Sensors | Basic self-defense, basic movement            | Combat effectiveness severely reduced                |
| **Utility (Vulcan)**| Specialized Role Systems (Construction, Repair, E-War)      | Self-defense, C&C for role-specific tasks     | General combat/PoW output low                        |
| **Balanced (Default)**| Dynamic allocation based on AI logic & immediate threats    | All systems operate at moderate efficiency    | No system at peak performance, susceptible to overload |

    *   **6.4.2. "Fork" Allocation Logic (Conceptual Pseudocode within `Unit.update()`)**:
```pseudocode
function allocateComputroniumForks(unit):
    availableForks = unit.computroniumCoreLevel.forkCount
    requestedForks = [] // List of {function, priority, forksNeeded}

    // Populate requestedForks based on active systems & current CoreFocusMode
    if unit.isFiringWeapon1: requestedForks.add(weapon1, getPriority(weapon1, unit.coreFocusMode), 1)
    if unit.isMultiTargeting and unit.coreFocusMode == Offensive: requestedForks.add(multiTargetAI, highPriority, 1) // or more
    if unit.isContributingToPoW and unit.coreFocusMode == C_C: requestedForks.add(PoW_module, mediumPriority, 1)
    // ... and so on for all systems

    sort requestedForks by priority (descending)

    allocatedFunctions = {}
    for request in requestedForks:
        if availableForks >= request.forksNeeded:
            allocatedFunctions[request.function] = "OPTIMAL"
            availableForks -= request.forksNeeded
        else if availableForks > 0 and request.forksNeeded > 1: // Can it run degraded?
            allocatedFunctions[request.function] = "DEGRADED" // Runs slower/less effectively
            availableForks = 0 // Consumed remaining partial fork
        else:
            allocatedFunctions[request.function] = "STARVED" // Doesn't run or runs very poorly

    // Apply performance modifiers based on allocatedFunctions status
    // e.g., if weapon1 is "DEGRADED", reduce its RoF or accuracy this tick
```

## 7. Combat Systems

*   **7.1. Damage Types & Effects Table**

| Damage Type              | Source Examples                                  | Primary Effect on Target                       | Secondary Effect(s)                                                                | Strong Against                        | Weak Against                               | Shield Interaction (General) |
| :----------------------- | :----------------------------------------------- | :--------------------------------------------- | :--------------------------------------------------------------------------------- | :------------------------------------ | :----------------------------------------- | :--------------------------- |
| **Kinetic/Ballistic**    | Cannons, Railguns, Mass Drivers                  | HP Damage (Physical)                           | Knockback, Stun (vs. light units)                                                  | Lightly Armored Units, Unshielded     | Heavy Armor, Specialized Kinetic Dampers | Bypassed by high mass/velocity or depletes shield energy |
| **Energy (Laser/Beam)**  | Pulse Lasers, Particle Beams                     | HP Damage (Thermal), Shield Damage             | Armor Ablation (reduces armor over time)                                           | Shields, Light/Medium Armor           | Heat-Resistant Alloys, Energy Shields (reflective) | Efficient shield depletion   |
| **Thermal (Plasma/Flame)** | Plasma Cannons, Flamers, Incendiary            | HP Damage (Often DoT), Overheat Status       | Reduces target performance (speed, RoF)                                            | Unarmored, Organic (if any), Structures | ThermoRegulantCeramics, Active Cooling | Moderate shield depletion    |
| **Electromagnetic (EMP)**| EMP Generators, EMP Warheads                     | Shield Drain/Collapse, Electronics Disable     | Temporary disables weapons, C&C, sensors, Computronium functions                   | Shields, Robotic/High-Tech Units      | EMP Hardening, Primitive/Low-Tech Units  | Massive shield damage/drain  |
| **Corrosive (Acid/Goo)** | Computronium-converted "Sticky" payloads         | HP Damage (DoT), Armor Degradation             | Slows movement                                                                     | Heavy Armor (over time), Structures   | Fast Units, Self-Repairing Materials     | Can bypass or stick to shields |
| **Nanite (Disassembler)**| Computronium-converted Nanite Swarms             | HP/Armor Damage (Slow, persistent DoT)         | Spreads if not countered                                                           | All (if given time)                   | Nanite Countermeasures, EMP (disrupts nanites) | Bypasses shields             |
| **Phase/Dimensional**    | Experimental Computronium-driven projectiles     | Internal HP Damage (ignores some armor/shields) | Potential for minor C&C/sensor disruption ("reality glitch")                         | Heavily Armored/Shielded Elites       | Units with Phase Dampeners             | Partially or fully bypasses shields |

*   **7.2. Weapon Systems & Capacitance**
    *   Weapons have `fireRate`, `damagePerShot`, `range`, `projectileSpeed`, `energyCostPerShot`, `capacitorSize`, `capacitorChargeRate`.
    *   Firing draws from capacitor; capacitor recharges from unit's battery (limited by `batteryRechargeRate_ps` and overall faction Energy).
    *   Cooldowns due to capacitor recharge, thermal limits, or mechanical reload.
    *   **7.2.1. Weapon Archetype Table (Example `WeaponData`)**

| Weapon ID               | Display Name         | Damage Type(s)      | Base Damage | Range | RoF (shots/sec) | Energy/Shot | Capacitor Size | Notes                                                              |
| :---------------------- | :------------------- | :------------------ | :---------- | :---- | :-------------- | :---------- | :------------- | :----------------------------------------------------------------- |
| `laser_pulse_light`     | Light Pulse Laser    | Energy, Thermal     | 15          | 400   | 2.0             | 10          | 50             | Good vs shields, low armor                                         |
| `cannon_heavy_kinetic`  | Heavy Kinetic Cannon | Kinetic             | 100         | 700   | 0.2             | 5           | 20             | High alpha, slow RoF, good vs structures                           |
| `emp_launcher_medium`   | Medium EMP Launcher  | EMP                 | 5 (HP)      | 500   | 0.5             | 50          | 150            | Drains 500 shield HP, 3s electronics disable in AoE              |
| `acid_goo_projector_t3` | Acid Goo Projector   | Corrosive, Thermal  | 10 (DoT)    | 300   | 1.0 (spray)     | 75          | 300            | Computronium-driven, high Energy/Computronium cycle cost per use |

*   **7.3. Shield Mechanics:**
    *   `Unit.maxShieldHP`, `Unit.shieldRechargeRate`, `Unit.shieldRechargeDelay_afterHit`.
    *   Shields absorb damage based on `DamageType.shieldInteractionFactor`. EMP is highly effective.
    *   Some shields might have "bleed-through" for very high "wattage" attacks (see SupCom).
    *   Shield harmonics (advanced tech) can be tuned to better resist specific damage types, consuming Computronium cycles.
*   **7.4. Armor Mechanics:**
    *   `Unit.armorValue` reduces incoming HP damage. `Damage = IncomingDamage * (1 - (ArmorValue / (ArmorValue + DamageConstant)))` (example formula).
    *   `Unit.armorType` provides percentage resistance/vulnerability to specific damage types (e.g., ThermoRegulantCeramic gives +50% resist vs. Thermal).

---

## 8. Command & Control (C&C)

*   **8.1. Hierarchical Command & Rank Bonuses:**
    *   **Ranks:** As previously defined (GENERAL, COLONEL, etc.). Units can gain XP and rank up, or high-rank units are built directly.
    *   **Command Aura / Radius:** Higher-rank units (especially Commanders) project an aura.
        *   **Effects within Aura (configurable by unit type/rank):**
            *   Increased subordinate unit accuracy, rate of fire, or shield recharge.
            *   Reduced cooldowns for abilities.
            *   Faster Computronium "fork" switching or slightly increased "fork" availability for subordinates.
            *   Improved resistance to C&C disruption (e.g., higher PoW validation threshold for incoming commands).
            *   Access to advanced squad formations or coordinated fire abilities.
        *   **Stacking:** Aura effects from multiple commanders might have diminishing returns or only the highest-rank aura applies.
    *   **Command Chain:** Orders can flow down the hierarchy. A GENERAL issues a strategic directive; a COLONEL within that directive's scope can issue more specific tactical orders to MAJORs, and so on. This allows for distributed C&C.
    *   **Data:** `Unit.Rank`, `CommanderUnitType.AuraRadius`, `CommanderUnitType.AuraEffects { type: String, bonusValue: Float }`
*   **8.2. Latency, Prediction, & "Light Seconds":**
    *   **Mechanics:** As detailed in Section II.B. (of a presumed prior document, for context here: latency affects command execution time).
        *   `effectiveLatency = baseDistanceLatency_m_per_tick * distance_m + dimensionalInstabilityFactor_tick_per_sector + (computroniumProcessingDelay_cycles / unit.Cores.cyclesPerTick)`
        *   Player/AI *must* issue commands anticipating this latency.
        *   **Predictive C&C AI:** Higher `computroniumCoreLevel` allows units/commanders to run more sophisticated predictive models to:
            *   Estimate enemy movement and future states.
            *   Adjust timing of queued abilities/attacks to land accurately despite latency.
            *   Propose optimal command sequences to the player.
        *   **Visual Feedback:** UI elements showing estimated command arrival time, potential "stale command" warnings.
*   **8.3. Squad AI & Contextual Behavior:**
    *   **Squad Definition:** A player-defined or AI-formed group of units operating under a leader (highest rank unit in group, or designated).
    *   **Squad Directives:** Receive broad objectives from higher command (e.g., "Patrol Zone X," "Secure Objective Y," "Engage Hostiles of Opportunity").
    *   **Contextual Actions (driven by unit AI & Computronium "forks"):**
        *   Automatic target prioritization based on threat level, unit type, RoE.
        *   Use of cover and terrain.
        *   Dynamic formation adjustments (e.g., spread out under artillery, tighten for focused fire).
        *   Automatic use of special abilities based on context (e.g., scout uses sensor ping if undetected enemies suspected).
        *   Calling for support (e.g., artillery request, repair request) if C&C link allows.
    *   **RoE Settings (Rules of Engagement):** Player/AI can set for squads:
        *   `AggressionLevel`: (Passive, Defensive, Aggressive, Reckless)
        *   `EngageRange`: (Short, Medium, Long, Max Weapon Range)
        *   `RetreatThreshold_HP_Percent`: At what HP units attempt to disengage.
        *   `PoWUsagePolicy_C_C`: (Minimal, Standard, High Security - affects local C&C processing load)
        *   `CoreFocusMode_Bias_Squad`: Preferred Computronium Core focus for units in the squad.

## 9. Proof-of-Work (PoW) & Computational Warfare

*   **9.1. Defensive PoW (Internal C&C Security):**
    *   **Mechanism:** Commands/data packets within a faction's network are stamped with a PoW solution.
        *   `PoWChallengeDifficulty_Internal` is a faction-wide setting, can be increased with tech/policy (at higher continuous Computronium cost).
        *   Receiving units validate the PoW using a small number of their Computronium "forks." Failure means packet is ignored/flagged.
    *   **Cost:** Constant drain on faction's `TotalComputeCapacity` and individual unit `ComputroniumCoreCycles` for validation.
*   **9.2. Offensive PoW (Breach Tools, CSA):**
    *   **Generation:**
        *   Faction dedicates a portion of its `TotalComputeCapacity` (from all Computronium Cores set to "C&C" or "PoW Generation" focus, plus dedicated PoW Array structures) to generate "Offensive PoW Units" (OPWU).
        *   `Faction.OPWU_GenerationRate = (Sum_of_Contributing_Core_Cycles * PoW_Algorithm_Efficiency_Factor) / Cycles_Per_OPWU`
        *   Qubit Cores provide a massive multiplier to `PoW_Algorithm_Efficiency_Factor`.
    *   **Mechanisms & Costs (OPWU expenditure):**
        *   **a. Computational Exhaustion:** Target enemy faction/node. Cost: OPWU per second, proportional to target's `PoWChallengeDifficulty_Internal` and desired flood intensity.
        *   **b. Command Injection:** Requires overcoming target's `PoWChallengeDifficulty_Internal`. Cost: High burst of OPWU for each attempted injection. Success chance based on OPWU spent vs. target difficulty.
        *   **c. CSA (Chronological Sync Attack):** Targets enemy Commander's "Avatar." Cost: Massive continuous OPWU drain, must exceed `TargetCommander.EstimatedSyncPoWThreshold * CSA_OVERWHELM_FACTOR`.
        *   **d. Meta-Network Attacks (Section 11.2):** Similar OPWU costs for attacking DHT integrity, data pointers, or community PoW challenges.
*   **9.3. PoW Generation & Consumption Mechanics:**
    *   **PoW Arrays (Structure):** Dedicated structures that consume large amounts of Energy to run many basic Computronium processing units solely for PoW generation (Defensive or Offensive pool).
    *   **Unit Contribution:** Individual units (especially those in "C&C Focus" mode) contribute their spare Computronium cycles to the faction's PoW pool.
    *   **Faction PoW Pool:** A global faction resource: `Faction.DefensivePoW_Reserve`, `Faction.OffensivePoW_Units_Available`.
    *   **Strategic Allocation:** Player/Strategic AI decides how to allocate generated PoW between bolstering internal defenses, launching offensive PoW attacks, or contributing to meta-network activities.

## 10. Data Architecture & Determinism ("TrikeShed")

*   **10.1. Deterministic Simulation Principles:**
    *   **Fixed Game Logic:** All game rules, physics, AI algorithms are consistent.
    *   **Seeded RNG:** A single initial seed (`gameRNG_seed`) for the match determines all pseudo-random outcomes. `gameRNG.getNext()` is called predictably.
    *   **Floating Point Consistency:** Strict handling of floating-point arithmetic to avoid cross-platform desyncs (e.g., fixed-point math for critical calculations, or engine-enforced IEEE 754 compliance).
    *   **Order of Operations:** Strict, defined order for unit updates, event processing within each game tick.
*   **10.2. Replay System & "Free Tick" AI:**
    *   **Replay File Contents:**
        *   `InitialGameState { mapID, gameRNG_seed, playerFactionAssignments, startingUnits_and_positions }`
        *   `CommandLog: Array<TimestampedCommand>`
            *   `TimestampedCommand { tickNumber, playerId_or_AI_source, commandData { type, params } }`
    *   **"Free Tick" AI:** (As per presumed prior document, Section VIII.B: AI actions based on RNG and logic aren't logged, only player/strategic AI commands and major unpredictable events).
    *   **Replay Playback:** Engine re-simulates the game from `InitialGameState`, applying `CommandLog` entries at the correct `tickNumber`.
*   **10.3. Idempotent Data & Immutability (Conceptual - "TrikeShed" Backend):**
    *   For persistent storage (e.g., match history database, meta-network content).
    *   Match replays (command logs + initial state hash) are content-addressable.
    *   Shared community assets (maps, mods - if applicable) on the meta-network are content-addressable (IPFS-like).
    *   This ensures verifiability and efficient storage of historical data.

## 11. Decentralized Meta-Network (Community & "Dogfooding")

*   **11.1. Kademlia/IPFS-Inspired Architecture:**
    *   **Purpose:** Player-to-player sharing of replays, mods (if supported), maps, strategy guides, faction propaganda. Not for real-time gameplay state.
    *   **Nodes:** Game clients can optionally act as nodes in this DHT.
    *   **Data Storage:** Content-addressed data blobs (replays, etc.) distributed across participating nodes.
    *   **Discovery:** Kademlia-like routing for finding content by hash.
*   **11.2. PoW for Meta-Network Integrity & Offense:**
    *   **Publishing Cost:** To upload a significant piece of content (e.g., a large replay, a new map), the publishing client must solve a PoW challenge. Difficulty `PoWChallengeDifficulty_Meta_Publish` is global or dynamically adjusted. Prevents spam.
    *   **Data Validation (Optional):** Popular content might require periodic "PoW renewal" from hosting nodes to remain easily discoverable, preventing "stale" or abandoned data from cluttering the network.
    *   **Offensive Meta-PoW:**
        *   A faction's *in-game* `Faction.OffensivePoW_Units_Available` can be allocated to generate PoW solutions for the *meta-network*.
        *   **Targets:**
            *   Attempt to "out-PoW" legitimate publishers to get malicious/false content prioritized or to de-list rival content.
            *   DDoS specific community nodes by flooding them with PoW-heavy verification requests.
            *   If meta-network has voting/reputation, use PoW to illegitimately boost own faction's standing or smear rivals.

## 12. Technology Tree & Research

*   **12.1. Research Categories (Conceptual):**
    *   **Material Science:** Unlocks new Alloys, improved mineral refining, Computronium synthesis grades.
    *   **Armaments & Defenses:** New weapon types, shield technologies, armor improvements, counter-measures.
    *   **Computronium & AI:** Higher Computronium Core levels, more efficient "fork" allocation, advanced AI routines (prediction, squad tactics), Qubit tech (endgame).
    *   **C&C Systems:** Improved C&C latency reduction, advanced predictive modeling, higher PoW security levels, CSA techniques/countermeasures.
    *   **Resource & Economy:** More efficient Quarry Machines, Energy Conversion Reactors, specialized Detector Widgets.
    *   **Exotic/Dimensional Tech:** Phase technology, advanced E-Warfare, experimental Computronium conversions.
*   **12.2. Tech Unlocks & Dependencies:**
    *   Visual tech tree with prerequisites.
    *   Research projects require:
        *   `ResearchPoints` (accumulated by Research Labs).
        *   `EnergyCost_Sustained`
        *   `ComputroniumCycleCost_Sustained` (for computational research tasks).
        *   `Time_seconds`
    *   Some techs might require specific salvaged Remnant artifacts or data from the meta-network.
    *   **Data:** `TechNode { id, displayName, description, cost {rp, energy, computronium_cycles, time}, prerequisites:Array<TechID>, unlocks:Array<UnitTypeID | AbilityID | AlloyBlueprintID | etc.} }`

## 13. User Interface (UI) & User Experience (UX) Considerations

*   **A. Strategic Layer UI:**
    *   Clear display of resources, production queues, research progress.
    *   Mineralogical map overlay with scan data.
    *   C&C latency indicators, command queue visualization.
    *   PoW generation/allocation interface.
    *   Squad management panel (RoE, formations, core focus bias).
*   **B. Tactical Layer UI:**
    *   Unit selection & status (HP, shields, energy, current core focus, active "forks" visualization - perhaps subtle).
    *   Clear weapon range indicators, ability cooldowns.
    *   FPS "tele-presence" view seamlessly integrated.
*   **C. Meta-Network UI:**
    *   Browser for community content (replays, guides).
    *   Interface for publishing content (shows PoW cost).
    *   Status of client as a P2P node.
*   **D. Feedback Mechanisms:**
    *   Visual cues for landscape degradation, Quarry Machine operation.
    *   Auditory/visual feedback for Computronium core under heavy load ("Dining Philosophers" contention).
    *   Clear notifications for C&C disruptions, CSA attempts.
    *   "Fog of War" and "Fog of Information" (incomplete sensor data, unknown mineral concentrations).
*   **E. Accessibility:**
    *   Customizable keybinds, UI scaling, colorblind modes.
    *   Detailed tooltips explaining complex mechanics.
    *   Comprehensive tutorial system for the steep learning curve.

---

## 14. Future Expansion / DLC Hooks (Placeholder)

*(Content for Future Expansion / DLC Hooks would detail potential avenues for post-launch content, new factions, mechanics, or story elements.)*
 
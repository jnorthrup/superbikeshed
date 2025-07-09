package rtsgame.systems

import rtsgame.core.*
import rtsgame.components.*

/**
 * Advanced combat system for RTS combat mechanics
 */
class AdvancedCombatSystem : System {
    override fun update(world: ECSWorld, deltaTime: Float) {
        // Update weapon cooldowns
        updateWeaponCooldowns(world, deltaTime)
        
        // Process combat actions
        processCombatActions(world, deltaTime)
        
        // Handle shield regeneration
        updateShieldRegeneration(world, deltaTime)
    }
    
    private fun updateWeaponCooldowns(world: ECSWorld, deltaTime: Float) {
        val entitiesWithWeapons = world.getEntitiesWithComponents(ComponentTypeId.WEAPON)
        
        entitiesWithWeapons.forEach { entityId ->
            val weapon = world.getComponent<WeaponComponent>(entityId, ComponentTypeId.WEAPON)
            if (weapon != null) {
                val updatedWeapon = weapon.updateCooldown(deltaTime)
                world.addComponent(entityId, updatedWeapon)
            }
        }
    }
    
    private fun processCombatActions(world: ECSWorld, deltaTime: Float) {
        val entitiesWithCombat = world.getEntitiesWithComponents(
            ComponentTypeId.POSITION,
            ComponentTypeId.WEAPON,
            ComponentTypeId.COMMAND
        )
        
        entitiesWithCombat.forEach { entityId ->
            val position = world.getComponent<PositionComponent>(entityId, ComponentTypeId.POSITION)
            val weapon = world.getComponent<WeaponComponent>(entityId, ComponentTypeId.WEAPON)
            val command = world.getComponent<CommandComponent>(entityId, ComponentTypeId.COMMAND)
            
            if (position != null && weapon != null && command != null) {
                when (command.commandType) {
                    "attack" -> {
                        if (command.targetId != null && weapon.canFire()) {
                            val targetEntityId = EntityId(command.targetId)
                            if (world.hasEntity(targetEntityId)) {
                                val targetPosition = world.getComponent<PositionComponent>(targetEntityId, ComponentTypeId.POSITION)
                                if (targetPosition != null) {
                                    val distance = position.distanceTo(targetPosition)
                                    if (distance <= weapon.range) {
                                        // Fire weapon
                                        val firedWeapon = weapon.fire()
                                        world.addComponent(entityId, firedWeapon)
                                        
                                        // Apply damage to target
                                        applyDamage(world, targetEntityId, weapon.damage)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun applyDamage(world: ECSWorld, targetEntityId: EntityId, damage: Float) {
        // Check if target has shields first
        val shield = world.getComponent<ShieldComponent>(targetEntityId, ComponentTypeId.SHIELD)
        if (shield != null && shield.currentShields > 0f) {
            // Damage shields first
            val remainingDamage = damage - shield.currentShields
            val newShield = shield.takeDamage(damage)
            world.addComponent(targetEntityId, newShield)
            
            // Apply remaining damage to health
            if (remainingDamage > 0f) {
                applyHealthDamage(world, targetEntityId, remainingDamage)
            }
        } else {
            // Apply damage directly to health
            applyHealthDamage(world, targetEntityId, damage)
        }
    }
    
    private fun applyHealthDamage(world: ECSWorld, targetEntityId: EntityId, damage: Float) {
        val health = world.getComponent<HealthComponent>(targetEntityId, ComponentTypeId.HEALTH)
        if (health != null) {
            val newHealth = health.takeDamage(damage)
            world.addComponent(targetEntityId, newHealth)
            
            // Check if entity is dead
            if (!newHealth.isAlive()) {
                // TODO: Trigger death event or remove entity
                // world.destroyEntity(targetEntityId)
            }
        }
    }
    
    private fun updateShieldRegeneration(world: ECSWorld, deltaTime: Float) {
        val entitiesWithShields = world.getEntitiesWithComponents(ComponentTypeId.SHIELD)
        
        entitiesWithShields.forEach { entityId ->
            val shield = world.getComponent<ShieldComponent>(entityId, ComponentTypeId.SHIELD)
            if (shield != null) {
                val regeneratedShield = shield.regenerate(deltaTime)
                world.addComponent(entityId, regeneratedShield)
            }
        }
    }
} 
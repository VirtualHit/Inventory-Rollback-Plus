package me.danjono.inventoryrollback.listeners;

import com.nuclyon.technicallycoded.inventoryrollback.InventoryRollbackPlus;
import com.nuclyon.technicallycoded.inventoryrollback.nms.EnumNmsVersion;

import me.NoChance.PvPManager.PvPlayer;
import me.NoChance.PvPManager.Events.PlayerCombatLogEvent;
import me.danjono.inventoryrollback.config.ConfigData;
import me.danjono.inventoryrollback.data.LogType;
import me.danjono.inventoryrollback.inventory.SaveInventory;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.projectiles.BlockProjectileSource;
import org.bukkit.projectiles.ProjectileSource;

public class EventLogs implements Listener {

	private InventoryRollbackPlus main;

	public EventLogs() {
		this.main = InventoryRollbackPlus.getInstance();
	}
	
	/**
	 * @author Atog
	 */
	@EventHandler(priority = EventPriority.HIGHEST)
	private void combatLog(PlayerCombatLogEvent event) {
	    if (!ConfigData.isEnabled()) {
	        return;
	    }

	    Player player = event.getPlayer();
	    Player enemy = event.getPvPlayer().getEnemy().getPlayer();
	    
	    new SaveInventory(player, LogType.DEATH, null, "CombatLog (" + enemy.getName() + ")", player.getInventory(), player.getEnderChest()).createSave(true);
	}
	
	/**
     * @author Atog
     */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onSuicide(PlayerCommandPreprocessEvent event) {
	    Player player = event.getPlayer();
	    PvPlayer pvp = PvPlayer.get(event.getPlayer());
	    if(!event.getMessage().startsWith("/suicide") || !ConfigData.isEnabled() || pvp.isInCombat() || !player.hasPermission("essentials.suicide")) {
	        return;
	    }
	    
	    new SaveInventory(event.getPlayer(), LogType.DEATH, null, "Suicide (/suicide)", player.getInventory(), player.getEnderChest()).createSave(true);;
	}
	
	/**
	 * @author Atog
	 */
	//@EventHandler(priority = EventPriority.MONITOR)
	public void onKill(PlayerCommandPreprocessEvent event) {
	    Player caster = event.getPlayer();
	    String[] command = event.getMessage().split(" ");
	    if(!command[0].startsWith("/kill") || !ConfigData.isEnabled() || !caster.hasPermission("essentials.kill")) {
	        return;
	    }
	    
	    Player target = Bukkit.getPlayer(command.length == 0 ? caster.getName() : command[1]);
	    
	    if(target == null || !target.isOnline()) {
	        return;
	    }
	    
	    new SaveInventory(target, LogType.DEATH, null, "/kill (by " + caster.getName() + ")", target.getInventory(), target.getEnderChest());
	}
	
	@EventHandler
	private void playerJoin(PlayerJoinEvent e) {
		if (!ConfigData.isEnabled()) return;

		Player player = e.getPlayer();
		if (player.hasPermission("inventoryrollbackplus.joinsave")) {
			new SaveInventory(e.getPlayer(), LogType.JOIN, null, null, player.getInventory(), player.getEnderChest()).createSave(true);
		}
		if (player.hasPermission("inventoryrollbackplus.adminalerts")) {
			// can send info to admins here
		}
	}

	@EventHandler
	private void playerQuit(PlayerQuitEvent e) {
		if (!ConfigData.isEnabled()) return;

		Player player = e.getPlayer();

		if (player.hasPermission("inventoryrollbackplus.leavesave")) {
			new SaveInventory(e.getPlayer(), LogType.QUIT, null, null, player.getInventory(), player.getEnderChest()).createSave(true);
		}
	}

	/**
	 * Handle saving the player's inventory on death.
	 * @param event Bukkit damage event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	private void playerDeath(EntityDamageEvent event) {
		// Sanity checks to prevent unwanted saves
		if (!ConfigData.isEnabled()) return;
		if (!(event.getEntity() instanceof Player)) return;
		if (event.isCancelled()) return;

		Player player = (Player) event.getEntity();

		// Check that the player actually died from the damage & that the player has the permission for inventory saves
		if (player.getHealth() - event.getFinalDamage() <= 0 && player.hasPermission("inventoryrollbackplus.deathsave")) {

			// Detailed reason for the death that can be applied given certain conditions
			String reason = null;

			// Handler the case where the death is caused by an entity
			if (isEntityCause(event.getCause()) && event instanceof EntityDamageByEntityEvent) {
				EntityDamageByEntityEvent damageByEntityEvent = (EntityDamageByEntityEvent) event;
				Entity damager = damageByEntityEvent.getDamager();

				// Get the shooter's name if the killing entity is a projectile
				String shooterName = "";
				if (damager instanceof Projectile) {

					Projectile proj = (Projectile) damager;
					ProjectileSource shooter = proj.getShooter();

					// Show shooter name if it's a living entity
					if (shooter instanceof LivingEntity) {
						LivingEntity shooterEntity = (LivingEntity) shooter;
						shooterName = ", " + shooterEntity.getName();
					}
					// Show shooter block type if it's a block projectile source
					else if (shooter instanceof BlockProjectileSource) {
						BlockProjectileSource shooterBlock = (BlockProjectileSource) shooter;
						shooterName = ", " + shooterBlock.getBlock().getType().name();

					}
					// In all other cases, don't show projectile detailed shooter info
				}

				// Create a more specific reason given the data above
				reason = event.getCause().name() + " (" + damageByEntityEvent.getDamager().getName() + shooterName + ")";
			}

			// CONVERT CAUSE
			DamageCause cause = event.getCause();
			
			if(cause == DamageCause.CUSTOM) {
			    reason = "/kill";
			} else if(cause == DamageCause.SUICIDE) {
			    reason = "Suicidio o self /kill";
			}
			
			// After all checks, create the save with data provided above
			new SaveInventory(player, LogType.DEATH, cause, reason, player.getInventory(), player.getEnderChest()).createSave(true);
		}
	}

	@EventHandler
	private void playerChangeWorld(PlayerChangedWorldEvent e) {
		if (!ConfigData.isEnabled()) return;

		Player player = e.getPlayer();

		if (player.hasPermission("inventoryrollbackplus.worldchangesave")) {
			new SaveInventory(e.getPlayer(), LogType.WORLD_CHANGE, null, null, player.getInventory(), player.getEnderChest()).createSave(true);
		}
	}

	public boolean isEntityCause(EntityDamageEvent.DamageCause cause) {
		if (cause.equals(EntityDamageEvent.DamageCause.ENTITY_ATTACK) ||
				cause.equals(EntityDamageEvent.DamageCause.PROJECTILE)) return true;
		if (this.main.getVersion().isAtLeast(EnumNmsVersion.v1_11_R1)) {
			if (cause.equals(EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) return true;
		}
		return false;
	}

}

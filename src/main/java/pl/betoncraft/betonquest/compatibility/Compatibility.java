/**
 * BetonQuest - advanced quests for Bukkit
 * Copyright (C) 2015  Jakub "Co0sh" Sapalski
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pl.betoncraft.betonquest.compatibility;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

import ch.njol.skript.Skript;
import de.slikey.effectlib.EffectManager;
import me.blackvein.quests.Quests;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.permission.Permission;
import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.compatibility.citizens.CitizensListener;
import pl.betoncraft.betonquest.compatibility.citizens.CitizensParticle;
import pl.betoncraft.betonquest.compatibility.citizens.CitizensWalkingListener;
import pl.betoncraft.betonquest.compatibility.citizens.NPCInteractObjective;
import pl.betoncraft.betonquest.compatibility.citizens.NPCKillObjective;
import pl.betoncraft.betonquest.compatibility.denizen.DenizenTaskScriptEvent;
import pl.betoncraft.betonquest.compatibility.effectlib.ParticleEvent;
import pl.betoncraft.betonquest.compatibility.heroes.HeroesClassCondition;
import pl.betoncraft.betonquest.compatibility.heroes.HeroesExperienceEvent;
import pl.betoncraft.betonquest.compatibility.heroes.HeroesMobKillListener;
import pl.betoncraft.betonquest.compatibility.heroes.HeroesSkillCondition;
import pl.betoncraft.betonquest.compatibility.magic.WandCondition;
import pl.betoncraft.betonquest.compatibility.mcmmo.McMMOAddExpEvent;
import pl.betoncraft.betonquest.compatibility.mcmmo.McMMOSkillLevelCondition;
import pl.betoncraft.betonquest.compatibility.mythicmobs.MythicMobKillObjective;
import pl.betoncraft.betonquest.compatibility.mythicmobs.MythicSpawnMobEvent;
import pl.betoncraft.betonquest.compatibility.playerpoints.PlayerPointsCondition;
import pl.betoncraft.betonquest.compatibility.playerpoints.PlayerPointsEvent;
import pl.betoncraft.betonquest.compatibility.quests.ConditionRequirement;
import pl.betoncraft.betonquest.compatibility.quests.EventReward;
import pl.betoncraft.betonquest.compatibility.quests.QuestCondition;
import pl.betoncraft.betonquest.compatibility.quests.QuestEvent;
import pl.betoncraft.betonquest.compatibility.shopkeepers.HavingShopCondition;
import pl.betoncraft.betonquest.compatibility.shopkeepers.OpenShopEvent;
import pl.betoncraft.betonquest.compatibility.skillapi.SkillAPIClassCondition;
import pl.betoncraft.betonquest.compatibility.skillapi.SkillAPILevelCondition;
import pl.betoncraft.betonquest.compatibility.skript.BQEventSkript;
import pl.betoncraft.betonquest.compatibility.skript.BQEventSkript.CustomEventForSkript;
import pl.betoncraft.betonquest.compatibility.skript.SkriptConditionBQ;
import pl.betoncraft.betonquest.compatibility.skript.SkriptEffectBQ;
import pl.betoncraft.betonquest.compatibility.skript.SkriptEventBQ;
import pl.betoncraft.betonquest.compatibility.vault.MoneyCondition;
import pl.betoncraft.betonquest.compatibility.vault.MoneyEvent;
import pl.betoncraft.betonquest.compatibility.vault.MoneyVariable;
import pl.betoncraft.betonquest.compatibility.vault.PermissionEvent;
import pl.betoncraft.betonquest.compatibility.worldguard.RegionCondition;
import pl.betoncraft.betonquest.compatibility.worldguard.RegionObjective;
import pl.betoncraft.betonquest.utils.Debug;

/**
 * Compatibility with other plugins
 * 
 * @author Jakub Sapalski
 */
public class Compatibility {

	private BetonQuest plugin = BetonQuest.getInstance();
	private static Compatibility instance;
	private List<String> hooked = new ArrayList<>();

	private Permission permission = null;
	private Economy economy = null;

	private EffectManager manager;

	public Compatibility() {
		instance = this;

		// hook into MythicMobs
		if (Bukkit.getPluginManager().isPluginEnabled("MythicMobs")
				&& plugin.getConfig().getString("hook.mythicmobs").equalsIgnoreCase("true")) {
			plugin.registerObjectives("mmobkill", MythicMobKillObjective.class);
			plugin.registerEvents("mspawnmob", MythicSpawnMobEvent.class);
			hooked.add("MythicMobs");
		}

		// hook into Citizens
		if (Bukkit.getPluginManager().isPluginEnabled("Citizens")
				&& plugin.getConfig().getString("hook.citizens").equalsIgnoreCase("true")) {
			new CitizensListener();
			new CitizensWalkingListener();
			plugin.registerObjectives("npckill", NPCKillObjective.class);
			plugin.registerObjectives("npcinteract", NPCInteractObjective.class);
			hooked.add("Citizens");
		}

		// hook into Vault
		if (Bukkit.getPluginManager().isPluginEnabled("Vault")
				&& plugin.getConfig().getString("hook.vault").equalsIgnoreCase("true")) {
			RegisteredServiceProvider<Permission> permissionProvider = Bukkit.getServer().getServicesManager()
					.getRegistration(net.milkbowl.vault.permission.Permission.class);
			if (permissionProvider != null) {
				permission = permissionProvider.getProvider();
			}
			RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager()
					.getRegistration(net.milkbowl.vault.economy.Economy.class);
			if (economyProvider != null) {
				economy = economyProvider.getProvider();
			}
			if (economy != null) {
				plugin.registerEvents("money", MoneyEvent.class);
				plugin.registerConditions("money", MoneyCondition.class);
				plugin.registerVariable("money", MoneyVariable.class);
			} else {
				Debug.error("There is no economy plugin on the server!");
			}
			if (permission != null) {
				plugin.registerEvents("permission", PermissionEvent.class);
			} else {
				Debug.error("Could not get permission provider!");
			}
			hooked.add("Vault");
		}

		// hook into Skript
		if (Bukkit.getPluginManager().isPluginEnabled("Skript")
				&& plugin.getConfig().getString("hook.skript").equalsIgnoreCase("true")) {
			Skript.registerCondition(SkriptConditionBQ.class, "%player% (meet|meets) [betonquest] condition %string%");
			Skript.registerEffect(SkriptEffectBQ.class, "fire [betonquest] event %string% for %player%");
			Skript.registerEvent("betonquest", SkriptEventBQ.class, CustomEventForSkript.class,
					"[betonquest] event %string%");
			plugin.registerEvents("skript", BQEventSkript.class);
			hooked.add("Skript");
		}

		// hook into WorldGuard
		if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")
				&& plugin.getConfig().getString("hook.worldguard").equalsIgnoreCase("true")) {
			plugin.registerConditions("region", RegionCondition.class);
			plugin.registerObjectives("region", RegionObjective.class);
			hooked.add("WorldGuard");
		}

		// hook into mcMMO
		if (Bukkit.getPluginManager().isPluginEnabled("mcMMO")
				&& plugin.getConfig().getString("hook.mcmmo").equalsIgnoreCase("true")) {
			plugin.registerConditions("mcmmolevel", McMMOSkillLevelCondition.class);
			plugin.registerEvents("mcmmoexp", McMMOAddExpEvent.class);
			hooked.add("mcMMO");
		}

		// hook into EffectLib
		if (Bukkit.getPluginManager().isPluginEnabled("EffectLib")
				&& plugin.getConfig().getString("hook.effectlib").equalsIgnoreCase("true")) {
			manager = new EffectManager(plugin);
			if (hooked.contains("Citizens"))
				new CitizensParticle();
			plugin.registerEvents("particle", ParticleEvent.class);
			hooked.add("EffectLib");
		}

		// hook into PlayerPoints
		if (Bukkit.getPluginManager().isPluginEnabled("PlayerPoints")
				&& plugin.getConfig().getString("hook.playerpoints").equalsIgnoreCase("true")) {
			plugin.registerEvents("playerpoints", PlayerPointsEvent.class);
			plugin.registerConditions("playerpoints", PlayerPointsCondition.class);
			hooked.add("PlayerPoints");
		}

		// hook into Heroes
		if (Bukkit.getPluginManager().isPluginEnabled("Heroes")
				&& plugin.getConfig().getString("hook.heroes").equalsIgnoreCase("true")) {
			plugin.registerConditions("heroesclass", HeroesClassCondition.class);
			plugin.registerConditions("heroesskill", HeroesSkillCondition.class);
			plugin.registerEvents("heroesexp", HeroesExperienceEvent.class);
			// create mobkill listener for passing Heroes kills to
			// MobKillObjective
			new HeroesMobKillListener();
			hooked.add("Heroes");
		}

		// hook into Magic
		if (Bukkit.getPluginManager().isPluginEnabled("Magic")
				&& plugin.getConfig().getString("hook.magic").equalsIgnoreCase("true")) {
			plugin.registerConditions("wand", WandCondition.class);
			hooked.add("Magic");
		}

		// hook into Denizen
		if (Bukkit.getPluginManager().isPluginEnabled("Denizen")
				&& plugin.getConfig().getString("hook.denizen").equalsIgnoreCase("true")) {
			plugin.registerEvents("script", DenizenTaskScriptEvent.class);
			hooked.add("Denizen");
		}

		// hook into SkillAPI
		if (Bukkit.getPluginManager().getPlugin("SkillAPI") != null
				&& plugin.getConfig().getString("hook.skillapi").equalsIgnoreCase("true")) {
			// SKillAPI 3.37 is already hooking into BetonQuest with outdated
			// conditions,
			// so I can't check if it is enabled, let's just hope it is
			plugin.registerConditions("skillapiclass", SkillAPIClassCondition.class);
			plugin.registerConditions("skillapilevel", SkillAPILevelCondition.class);
			hooked.add("SkillAPI");
		}

		// hook into Quests
		if (Bukkit.getPluginManager().getPlugin("Quests") != null
				&& plugin.getConfig().getString("hook.quests").equalsIgnoreCase("true")) {
			// because SkillAPI wants to load before Quests, that means Quests
			// will be forced to load
			// after BetonQuest, so I can't check if it's enabled either; let's
			// hope it is
			plugin.registerConditions("quest", QuestCondition.class);
			plugin.registerEvents("quest", QuestEvent.class);
			// Quests is not enabled yet (because of SkillAPI...) so add custom
			// stuff later
			new BukkitRunnable() {
				@Override
				public void run() {
					Quests.getInstance().customRewards.add(new EventReward());
					Quests.getInstance().customRequirements.add(new ConditionRequirement());
				}
			}.runTask(plugin);
			hooked.add("Quests");
		}
		
		// hook into Shopkeepers
		if (Bukkit.getPluginManager().isPluginEnabled("Shopkeepers")
				&& plugin.getConfig().getString("hook.shopkeepers").equalsIgnoreCase("true")) {
			plugin.registerEvents("shopkeeper", OpenShopEvent.class);
			plugin.registerConditions("shopamount", HavingShopCondition.class);
			hooked.add("Shopkeepers");
		}

		// log which plugins have been hooked
		if (hooked.size() > 0) {
			StringBuilder string = new StringBuilder();
			for (String plugin : hooked) {
				string.append(plugin + ", ");
			}
			String plugins = string.substring(0, string.length() - 2);
			plugin.getLogger().info("Hooked into " + plugins + "!");
		}
	}

	/**
	 * @return the permission
	 */
	public static Permission getPermission() {
		return instance.permission;
	}

	/**
	 * @return the economy
	 */
	public static Economy getEconomy() {
		return instance.economy;
	}

	/**
	 * @return the EffectLib effect manager
	 */
	public static EffectManager getEffectManager() {
		return instance.manager;
	}

	/**
	 * Reloads all stuff connected to other plugins.
	 */
	public static void reload() {
		if (instance.hooked.contains("Citizens") && instance.hooked.contains("EffectLib")) {
			CitizensParticle.reload();
		}
	}

	/**
	 * Is called when BetonQuest is being disabled. Does everything the
	 * compatible plugins require to do on disable.
	 */
	public void disable() {
		if (hooked.contains("EffectLib")) {
			manager.dispose();
		}
	}

}

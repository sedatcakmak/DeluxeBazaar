package me.sedattr.deluxebazaar.others;

import me.sedattr.deluxebazaar.DeluxeBazaar;
import org.bukkit.Bukkit;

public final class TaskUtils {
    public static boolean isFolia;

    static {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            isFolia = true;
        } catch (final ClassNotFoundException e) {
            isFolia = false;
        }
    }

    public static void run(Runnable runnable) {
        if (isFolia) {
            DeluxeBazaar.getInstance().getServer().getGlobalRegionScheduler().execute(DeluxeBazaar.getInstance(), runnable);
        } else {
            Bukkit.getScheduler().runTask(DeluxeBazaar.getInstance(), runnable);
        }
    }

    public static void runLater(Runnable runnable, long delayTicks) {
        if (isFolia) {
            DeluxeBazaar.getInstance().getServer().getGlobalRegionScheduler().runDelayed(DeluxeBazaar.getInstance(), task -> runnable.run(), delayTicks);
        } else {
            Bukkit.getScheduler().runTaskLater(DeluxeBazaar.getInstance(), runnable, delayTicks);
        }
    }

    public static void runAsync(Runnable runnable) {
        if (isFolia) {
            DeluxeBazaar.getInstance().getServer().getAsyncScheduler().runNow(DeluxeBazaar.getInstance(), task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(DeluxeBazaar.getInstance(), runnable);
        }
    }

    public static void runTimer(Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia) {
            DeluxeBazaar.getInstance().getServer().getGlobalRegionScheduler().runAtFixedRate(DeluxeBazaar.getInstance(), task -> runnable.run(), delayTicks, periodTicks);
        } else {
            Bukkit.getScheduler().runTaskTimer(DeluxeBazaar.getInstance(), runnable, delayTicks, periodTicks);
        }
    }
}
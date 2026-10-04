package me.clip.placeholderapi;
import org.bukkit.entity.Player;
public final class PlaceholderAPI {
  public static boolean throwing;
  public static String setPlaceholders(Player player,String text){if(throwing)throw new AssertionError("QA injected PAPI failure");return text.replace("%qa_value%","resolved");}
}

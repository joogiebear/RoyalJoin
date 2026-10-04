package com.mystipixel.royaljoin.qa;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** RoyalJoin-aware QA assertions; never packaged with the production plugin. */
public final class HarnessHelperPlugin extends JavaPlugin implements Listener {
  private final Map<String,PermissionAttachment> permissions=new HashMap<>();
  private NamespacedKey key; private int failures, invocations; private String lastPapiArgument=""; private String deathCase; private boolean deathDropsClean,deathKeep;
  @Override public void onEnable(){key=new NamespacedKey("royaljoin","item-id");getServer().getPluginManager().registerEvents(this,this);getLogger().info("HARNESS READY helper=4.0.0 assertions=full-item-state");}
  @Override public void onDisable(){permissions.values().forEach(PermissionAttachment::remove);}
  @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] a){
    if(a.length>0&&a[0].equals("invoked")){invoked(sender,a);return true;}
    Player p=sender instanceof Player x?x:a.length>1?Bukkit.getPlayerExact(a[1]):null;
    if(a.length==0||p==null){sender.sendMessage("HARNESS ERROR usage/player");return true;}
    try{switch(a[0]){
      case "assert-join"->assertConfiguredJoin(p);
      case "refresh"->{InventorySnapshot before=snapshot(p);reload();check("repeated-refresh-idempotent-full-state",before.equals(snapshot(p)),snap(p));}
      case "inventory"->inventory(p);
      case "permission"->permission(p);
      case "world"->world(p);
      case "reload-valid"->valid(p);
      case "reload-invalid"->invalid(p);
      case "empty"->empty(p);
      case "protect"->protect(p);
      case "real-protect"->check("protected-container-click-real-protocol",owned(p.getInventory().getItem(8),"menu")&&ownedCount(p)==1,"protocol=real-container-click "+snap(p));
      case "death"->death(p,a);
      case "commands"->commands(p,a);
      case "command-check"->commandCheck(a);
      case "papi"->papi(p,a);
      case "reset"->{write(configBase());reload();}
      case "dump"->getLogger().info("HARNESS SNAPSHOT "+snap(p));
      case "result"->sender.sendMessage("HARNESS RESULT failures="+failures);
      default->sender.sendMessage("HARNESS ERROR unknown-action="+a[0]);
    }}catch(Throwable t){fail(a[0],t.getClass().getSimpleName()+":"+t.getMessage());}return true;
  }
  @EventHandler(priority=EventPriority.MONITOR) public void observeDeath(PlayerDeathEvent e){
    if(deathCase==null||!e.getEntity().getName().equals("RoyalJoinQA"))return;
    deathDropsClean=e.getDrops().stream().noneMatch(this::isOwned);
  }
  @EventHandler(priority=EventPriority.MONITOR) public void observeRespawn(PlayerRespawnEvent e){
    if(deathCase==null)return; String n=deathCase; deathCase=null;
    Bukkit.getScheduler().runTaskLater(this,()->check(n,deathDropsClean&&owned(e.getPlayer().getInventory().getItem(8),"menu")&&amount(e.getPlayer(),Material.DIAMOND)==(deathKeep?1:0),"protocol=lifecycle dropsClean="+deathDropsClean+" keep="+deathKeep+" "+snap(e.getPlayer())),2L);
  }
  private void death(Player p,String[] a){
    boolean keep=a.length>1&&Boolean.parseBoolean(a[1]); p.getWorld().setGameRule(GameRule.KEEP_INVENTORY,keep);
    p.getInventory().clear();p.getInventory().setItem(0,new ItemStack(Material.DIAMOND));reload();deathDropsClean=false;deathKeep=keep;
    deathCase="death-keepinventory-"+keep+"-drops-respawn";p.setHealth(0);
  }
  private void commands(Player p,String[] a){
    String mode=a.length>1?a[1]:"player";invocations=0;
    write(switch(mode){case "console"->configCommand(true,0,0,1000,0);case "cooldown"->configCommand(false,1000,0,1000,0);case "burst"->configCommand(false,0,2,1000,1);default->configCommand(false,0,0,1000,0);});reload();
    try{Object plugin=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("RoyalJoin"));Object tracker=plugin.getClass().getMethod("cooldowns").invoke(plugin);tracker.getClass().getMethod("forget",Player.class).invoke(tracker,p);}catch(Exception e){throw new IllegalStateException(e);}
    check("command-fixture-player-ready",owned(p.getInventory().getItem(8),"menu"),"protocol=fixture "+snap(p));
  }
  private void commandCheck(String[] a){int expected=Integer.parseInt(a[1]);String name=a[2];check(name,invocations==expected,"protocol=real-use-item invocations="+invocations+" expected="+expected);}
  private void papi(Player p,String[] a){
    org.bukkit.plugin.Plugin papi=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("PlaceholderAPI"));String mode=a.length>1?a[1]:"";
    if(mode.equals("reset")){Bukkit.getPluginManager().enablePlugin(papi);setPapiThrowing(false);write(configBase());reload();return;}
    if(mode.equals("check")){String failure=a.length>2?a[2]:"unknown";check("papi-"+failure+"-command-fallback",invocations==1&&lastPapiArgument.equals("%qa_value%"),"protocol=real-use-item argument="+lastPapiArgument+" invocations="+invocations);return;}
    invocations=0;lastPapiArgument="";
    if(mode.equals("missing")){Bukkit.getPluginManager().disablePlugin(papi);}else if(mode.equals("throwing")){Bukkit.getPluginManager().enablePlugin(papi);setPapiThrowing(true);}else throw new IllegalArgumentException("papi mode");
    write(configPapi());reload();ItemStack item=p.getInventory().getItem(8);String failure=mode;
    check("papi-"+failure+"-display-name-fallback",plain(item.getItemMeta().displayName()).equals("%qa_value%"),"protocol=server-fixture value="+plain(item.getItemMeta().displayName()));
    check("papi-"+failure+"-lore-fallback",item.getItemMeta().lore()!=null&&item.getItemMeta().lore().size()==1&&plain(item.getItemMeta().lore().get(0)).equals("%qa_value%"),"protocol=server-fixture "+snap(p));
  }
  private void setPapiThrowing(boolean value){try{Class.forName("me.clip.placeholderapi.PlaceholderAPI").getField("throwing").setBoolean(null,value);}catch(Exception e){throw new IllegalStateException(e);}}
  private void invoked(CommandSender sender,String[] a){
    invocations++;String mode=a.length>1?a[1]:"";String player=a.length>2?a[2]:"";String slash=a.length>3?a[3]:"";
    if(mode.equals("papi")){lastPapiArgument=player;return;}
    check("command-"+mode+"-sender-substitution-normalization",(mode.equals("console")==!(sender instanceof Player))&&player.equals("RoyalJoinQA")&&slash.equals("normalized"),"protocol=real-use-item count="+invocations+" sender="+sender.getClass().getSimpleName());
  }
  private void protect(Player p){
    p.getInventory().clear();reload();p.getInventory().setHeldItemSlot(8);ItemStack locked=p.getInventory().getItem(8).clone();InventorySnapshot before=snapshot(p);InventoryView v=p.getOpenInventory();
    assertCancelled("protected-number-key-synthetic",new InventoryClickEvent(v,InventoryType.SlotType.CONTAINER,0,org.bukkit.event.inventory.ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,8),before,p);
    Map<Integer,ItemStack> dragged=Map.of(0,locked.clone());assertCancelled("protected-drag-synthetic",new InventoryDragEvent(v,new ItemStack(Material.AIR),locked.clone(),false,dragged),before,p);
    assertCancelled("protected-creative-synthetic",new InventoryClickEvent(v,InventoryType.SlotType.QUICKBAR,44,org.bukkit.event.inventory.ClickType.CREATIVE,InventoryAction.PLACE_ALL),before,p);
    assertCancelled("protected-container-synthetic",new InventoryClickEvent(v,InventoryType.SlotType.QUICKBAR,44,org.bukkit.event.inventory.ClickType.LEFT,InventoryAction.PICKUP_ALL),before,p);
    Item dropped=p.getWorld().dropItem(p.getLocation(),locked.clone());assertCancelled("protected-drop-synthetic",new PlayerDropItemEvent(p,dropped),before,p);dropped.remove();
    assertCancelled("protected-offhand-synthetic",new PlayerSwapHandItemsEvent(p,locked.clone(),null),before,p);
    ItemFrame frame=p.getWorld().spawn(p.getLocation(),ItemFrame.class);assertCancelled("protected-item-frame-synthetic",new PlayerInteractEntityEvent(p,frame,EquipmentSlot.HAND),before,p);frame.remove();
    Allay allay=p.getWorld().spawn(p.getLocation(),Allay.class);assertCancelled("protected-allay-synthetic",new PlayerInteractEntityEvent(p,allay,EquipmentSlot.HAND),before,p);allay.remove();
    ArmorStand stand=p.getWorld().spawn(p.getLocation(),ArmorStand.class);assertCancelled("protected-armor-stand-synthetic",new PlayerArmorStandManipulateEvent(p,stand,locked.clone(),null,EquipmentSlot.HAND,EquipmentSlot.HAND),before,p);stand.remove();
  }
  private void assertCancelled(String name,org.bukkit.event.Cancellable event,InventorySnapshot before,Player p){Bukkit.getPluginManager().callEvent((org.bukkit.event.Event)event);boolean conserved=before.equals(snapshot(p));check(name,event.isCancelled()&&conserved,"protocol=synthetic-bukkit cancelled="+event.isCancelled()+" fullStateConserved="+conserved);}
  private void inventory(Player p){
    p.getInventory().clear();p.getInventory().setItem(8,new ItemStack(Material.DIAMOND,7));p.getInventory().setItem(0,new ItemStack(Material.DIAMOND,60));reload();
    check("reserved-target-partial-merge",owned(p.getInventory().getItem(8),"menu")&&amount(p,Material.DIAMOND)==67,snap(p));
    p.getInventory().clear();for(int i=0;i<36;i++)p.getInventory().setItem(i,new ItemStack(Material.STONE,64));p.getInventory().setBoots(oldTaggedStack());p.getInventory().setItem(8,new ItemStack(Material.DIAMOND));
    ItemStack old=p.getInventory().getBoots();ItemMeta oldMeta=old==null?null:old.getItemMeta();NamespacedKey unrelated=new NamespacedKey(this,"fixture-note");
    boolean oldFixture=old!=null&&old.getType()==Material.PAPER&&old.getAmount()==7&&oldMeta!=null
      &&plain(oldMeta.displayName()).equals("Retired Royal Pass")&&oldMeta.lore()!=null&&oldMeta.lore().size()==2
      &&plain(oldMeta.lore().get(0)).equals("Preserve this lore")&&plain(oldMeta.lore().get(1)).equals("Rollback sentinel")
      &&oldMeta.getEnchantLevel(Enchantment.UNBREAKING)==2&&oldMeta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)
      &&oldMeta.hasCustomModelData()&&oldMeta.getCustomModelData()==7301&&owned(old,"retired")
      &&"unrelated-value-47".equals(oldMeta.getPersistentDataContainer().get(unrelated,PersistentDataType.STRING));
    check("full-inventory-old-tag-rich-metadata-fixture",oldFixture,"protocol=server-fixture slot=boots "+itemDetail(old));
    InventorySnapshot full=snapshot(p);reload();
    check("full-inventory-exact-rollback-old-tags",full.equals(snapshot(p)),snap(p));
    p.getInventory().clear();write(configTwo());reload();check("multiple-items-changed-slots",owned(p.getInventory().getItem(1),"menu")&&owned(p.getInventory().getItem(6),"second"),snap(p));
    write(configConflict());InventorySnapshot before=snapshot(p);reload();check("deterministic-conflict-rejection",before.equals(snapshot(p)),snap(p));
    write(configBase());p.getInventory().clear();reload();
  }
  private void permission(Player p){
    write(configPermission());reload();check("permission-absent",!hasOwned(p),snap(p));PermissionAttachment x=permissions.computeIfAbsent(p.getName(),n->p.addAttachment(this));x.setPermission("royaljoin.qa.use",true);p.recalculatePermissions();reload();check("permission-grant",owned(p.getInventory().getItem(8),"menu"),snap(p));x.setPermission("royaljoin.qa.use",false);p.recalculatePermissions();reload();check("permission-removal",!hasOwned(p),snap(p));write(configBase());reload();
  }
  private void world(Player p){
    World other=Optional.ofNullable(Bukkit.getWorld("harness_world")).orElseGet(()->Bukkit.createWorld(new WorldCreator("harness_world")));write(configWhitelist());reload();check("world-whitelist-included",hasOwned(p),snap(p));p.teleport(Objects.requireNonNull(other).getSpawnLocation());
    Bukkit.getScheduler().runTaskLater(this,()->{check("world-whitelist-excluded-transition",!hasOwned(p),snap(p));write(configBlacklist());reload();check("world-blacklist-included",hasOwned(p),snap(p));p.teleport(Objects.requireNonNull(Bukkit.getWorld("world")).getSpawnLocation());Bukkit.getScheduler().runTaskLater(this,()->{check("world-blacklist-excluded-transition",!hasOwned(p),snap(p));write(configBase());reload();},2L);},2L);
  }
  private void valid(Player p){write(configChanged());writeWorld("world.yml",configWorldChanged());reload();check("reload-valid-main-world-cooldown",owned(p.getInventory().getItem(3),"world-changed")&&activeEvidence(p).contains("between=17"),activeEvidence(p)+" "+snap(p));}
  private void invalid(Player p){
    InventorySnapshot before=snapshot(p);String active=activeEvidence(p);
    writeWorld("world.yml","items: [unterminated\n");boolean parseSuccess=reloadResult();check("reload-invalid-world-parse-retains-main-world-cooldown",!parseSuccess&&before.equals(snapshot(p))&&active.equals(activeEvidence(p)),"protocol=server-fixture before="+active+" after="+activeEvidence(p));
    writeWorld("world.yml","items:\n  broken:\n    slot: 99\n    material: STONE\n    command: harness dump\n");boolean semanticSuccess=reloadResult();check("reload-invalid-world-semantic-retains-main-world-cooldown",!semanticSuccess&&before.equals(snapshot(p))&&active.equals(activeEvidence(p)),"protocol=server-fixture before="+active+" after="+activeEvidence(p));
    deleteWorld("world.yml");write(configBase());reload();
  }
  private void empty(Player p){write("items: {}\n"+cooldown());reload();check("intentional-empty-items",!hasOwned(p),snap(p));write(configBase());reload();}
  /** Invoke the plugin's public reload API directly; nested command dispatch is queued by Paper. */
  private void reload(){reloadResult();}
  private boolean reloadResult(){try{
    Object plugin=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("RoyalJoin"));
    Object result=plugin.getClass().getMethod("reloadItems").invoke(plugin);
    boolean success=(boolean)result.getClass().getMethod("success").invoke(result);
    if(success){Object service=plugin.getClass().getMethod("itemService").invoke(plugin);for(Player p:Bukkit.getOnlinePlayers())service.getClass().getMethod("apply",Player.class).invoke(service,p);}return success;
  }catch(Exception e){throw new IllegalStateException(e);}}
  private void write(String body){try{Path p=getDataFolder().toPath().getParent().resolve("RoyalJoin/config.yml");Files.writeString(p,body);}catch(Exception e){throw new IllegalStateException(e);}}
  private void writeWorld(String name,String body){try{Path p=getDataFolder().toPath().getParent().resolve("RoyalJoin/worlds/"+name);Files.createDirectories(p.getParent());Files.writeString(p,body);}catch(Exception e){throw new IllegalStateException(e);}}
  private void deleteWorld(String name){try{Files.deleteIfExists(getDataFolder().toPath().getParent().resolve("RoyalJoin/worlds/"+name));}catch(Exception e){throw new IllegalStateException(e);}}
  private void check(String n,boolean ok,String d){if(ok)getLogger().info("CASE PASS name="+n+" detail="+d);else fail(n,d);}
  private void fail(String n,String d){failures++;getLogger().severe("CASE FAIL name="+n+" detail="+d);}
  private boolean owned(ItemStack i,String id){return i!=null&&i.hasItemMeta()&&id.equals(i.getItemMeta().getPersistentDataContainer().get(key,PersistentDataType.STRING));}
  private boolean isOwned(ItemStack i){return i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(key);}
  private boolean hasOwned(Player p){for(ItemStack i:p.getInventory().getContents())if(i!=null&&i.hasItemMeta()&&i.getItemMeta().getPersistentDataContainer().has(key))return true;return false;}
  private int ownedCount(Player p){int n=0;for(ItemStack i:p.getInventory().getContents())if(isOwned(i))n+=i.getAmount();return n;}
  private int amount(Player p,Material m){int n=0;for(ItemStack i:p.getInventory().getStorageContents())if(i!=null&&i.getType()==m)n+=i.getAmount();return n;}
  private ItemStack tag(Material m,String id){ItemStack i=new ItemStack(m);ItemMeta x=i.getItemMeta();x.getPersistentDataContainer().set(key,PersistentDataType.STRING,id);i.setItemMeta(x);return i;}
  private ItemStack oldTaggedStack(){
    ItemStack i=tag(Material.PAPER,"retired");i.setAmount(7);ItemMeta m=i.getItemMeta();
    m.displayName(Component.text("Retired Royal Pass"));m.lore(List.of(Component.text("Preserve this lore"),Component.text("Rollback sentinel")));
    m.addEnchant(Enchantment.UNBREAKING,2,true);m.addItemFlags(ItemFlag.HIDE_ENCHANTS);m.setCustomModelData(7301);
    m.getPersistentDataContainer().set(new NamespacedKey(this,"fixture-note"),PersistentDataType.STRING,"unrelated-value-47");i.setItemMeta(m);return i;
  }
  private void assertConfiguredJoin(Player p){ItemStack i=p.getInventory().getItem(8);ItemMeta m=i==null?null:i.getItemMeta();Set<NamespacedKey> keys=m==null?Set.of():m.getPersistentDataContainer().getKeys();boolean ok=i!=null&&i.getType()==Material.NETHER_STAR&&i.getAmount()==1&&m!=null&&plain(m.displayName()).equals("QA Menu")&&m.lore()!=null&&m.lore().size()==1&&plain(m.lore().get(0)).equals("%player%")&&keys.equals(Set.of(key))&&owned(i,"menu");check("join-configured-full-metadata",ok,"protocol=real-client "+snap(p));}
  private String plain(net.kyori.adventure.text.Component component){return component==null?"":PlainTextComponentSerializer.plainText().serialize(component);}
  private InventorySnapshot snapshot(Player p){return new InventorySnapshot(p.getInventory().getContents());}
  private record InventorySnapshot(Map<Integer,byte[]> slots){
    InventorySnapshot(ItemStack[] contents){this(serialized(contents));}
    private static Map<Integer,byte[]> serialized(ItemStack[] contents){Map<Integer,byte[]> result=new TreeMap<>();for(int i=0;i<contents.length;i++)if(contents[i]!=null&&!contents[i].getType().isAir())result.put(i,contents[i].serializeAsBytes());return result;}
    @Override public boolean equals(Object other){if(!(other instanceof InventorySnapshot that)||!slots.keySet().equals(that.slots.keySet()))return false;for(int slot:slots.keySet())if(!Arrays.equals(slots.get(slot),that.slots.get(slot)))return false;return true;}
    @Override public int hashCode(){int hash=1;for(var entry:slots.entrySet())hash=31*hash+entry.getKey()+Arrays.hashCode(entry.getValue());return hash;}
  }
  private String itemDetail(ItemStack i){if(i==null)return "null";ItemMeta m=i.getItemMeta();return i.getType()+"x"+i.getAmount()+" name="+(m==null?"":plain(m.displayName()))+" lore="+(m==null?null:m.lore())+" flags="+(m==null?null:m.getItemFlags())+" enchants="+i.getEnchantments()+" model="+(m!=null&&m.hasCustomModelData()?m.getCustomModelData():null)+" pdc="+(m==null?null:m.getPersistentDataContainer().getKeys().stream().collect(java.util.stream.Collectors.toMap(Object::toString,k->String.valueOf(m.getPersistentDataContainer().get(k,PersistentDataType.STRING)))));}
  private String snap(Player p){Map<Integer,String>s=new TreeMap<>();ItemStack[] all=p.getInventory().getContents();for(int i=0;i<all.length;i++)if(all[i]!=null&&!all[i].getType().isAir()){ItemMeta m=all[i].getItemMeta();String digest;try{digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all[i].serializeAsBytes()));}catch(Exception e){throw new IllegalStateException(e);}s.put(i,all[i].getType()+"x"+all[i].getAmount()+" name="+(m==null?"":plain(m.displayName()))+" lore="+(m==null?null:m.lore())+" flags="+(m==null?null:m.getItemFlags())+" enchants="+all[i].getEnchantments()+" model="+(m!=null&&m.hasCustomModelData()?m.getCustomModelData():null)+" pdcKeys="+(m==null?null:m.getPersistentDataContainer().getKeys())+" fullStateSha256="+digest);}return s.toString();}
  private String activeEvidence(Player p){try{Object plugin=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("RoyalJoin"));var itemMethod=plugin.getClass().getMethod("item",World.class,String.class);Object worldItem=itemMethod.invoke(plugin,p.getWorld(),"world-changed");Object mainItem=itemMethod.invoke(plugin,Bukkit.getWorld("harness_world"),"changed");Object cooldown=plugin.getClass().getMethod("cooldowns").invoke(plugin);return "mainItem="+(mainItem!=null)+" worldItem="+(worldItem!=null)+" between="+field(cooldown,"betweenUsesMillis")+" threshold="+field(cooldown,"spamThreshold")+" window="+field(cooldown,"spamWindowMillis")+" lockout="+field(cooldown,"lockoutMillis");}catch(Exception e){throw new IllegalStateException(e);}}
  private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
  private String item(String id,int slot,String mat,String extra){return "  "+id+":\n    slot: "+slot+"\n    material: "+mat+"\n    name: '&6QA "+id+"'\n    lore: ['&7%player%']\n    command: 'harness dump'\n    locked: true\n"+extra;}
  private String cooldown(){return "cooldown:\n  between-uses-ms: 0\n  spam-threshold: 0\n  spam-window-ms: 1000\n  lockout-seconds: 0\n";}
  private String configBase(){return "items:\n  menu:\n    slot: 9\n    material: NETHER_STAR\n    name: '&6QA Menu'\n    lore: ['&7%player%']\n    command: 'harness dump'\n    locked: true\n    glow: true\n    custom-model-data: 4242\n"+cooldown();}
  private String configTwo(){return "items:\n"+item("menu",2,"NETHER_STAR","")+item("second",7,"COMPASS","")+cooldown();}
  private String configConflict(){return "items:\n"+item("menu",2,"NETHER_STAR","")+item("second",2,"COMPASS","")+cooldown();}
  private String configPermission(){return "items:\n"+item("menu",9,"NETHER_STAR","    permission: royaljoin.qa.use\n")+cooldown();}
  private String configWhitelist(){return "items:\n"+item("menu",9,"NETHER_STAR","    worlds: [world]\n    world-mode: whitelist\n")+cooldown();}
  private String configBlacklist(){return "items:\n"+item("menu",9,"NETHER_STAR","    worlds: [world]\n    world-mode: blacklist\n")+cooldown();}
  private String configChanged(){return "items:\n"+item("changed",4,"EMERALD","")+"cooldown:\n  between-uses-ms: 17\n  spam-threshold: 2\n  spam-window-ms: 50\n  lockout-seconds: 1\n";}
  private String configWorldChanged(){return "items:\n"+item("world-changed",4,"EMERALD","")+"inherit-default: false\n";}
  private String configPapi(){return "items:\n  menu:\n    slot: 9\n    material: NETHER_STAR\n    name: '&6%qa_value%'\n    lore: ['&7%qa_value%']\n    command: 'harness invoked papi %qa_value% normalized'\n    locked: true\n"+cooldown();}
  private String configCommand(boolean console,long between,int threshold,long window,int lockout){return "items:\n  menu:\n    slot: 9\n    material: NETHER_STAR\n    name: '&6QA Menu'\n    lore: ['&7%player%']\n    command: '/harness invoked "+(console?"console":"player")+" %player% normalized'\n    as-console: "+console+"\n    locked: true\nclick: right\ncooldown:\n  between-uses-ms: "+between+"\n  spam-threshold: "+threshold+"\n  spam-window-ms: "+window+"\n  lockout-seconds: "+lockout+"\n  message: '&cQA lockout %seconds%'\n";}
}

package scripts.core.banking;

import org.tribot.api.General;
import org.tribot.api.Timing;
import org.tribot.api2007.*;
import org.tribot.api2007.types.RSItem;
import org.tribot.api2007.types.RSItemDefinition;
import org.tribot.script.sdk.Bank;
import org.tribot.script.sdk.Waiting;
import org.tribot.script.sdk.interfaces.Item;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.types.*;
import org.tribot.script.sdk.walking.GlobalWalking;
import scripts.core.botevent.BotEventv2;
import scripts.core.inventory.InventoryEvent;
import scripts.core.requistion.RequisitionItem;
import scripts.core.utilities.MethodsV2;
import scripts.core.utilities.WalkerV2;

import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.tribot.api2007.Inventory.getAll;
import static org.tribot.script.sdk.Log.debug;
import static org.tribot.script.sdk.Log.log;

public class BankEventV3 extends BotEventv2 {

    public  HashMap<String, BankCache> bankCacheHashMap = new HashMap<>();

    public LinkedHashMap<String, RequisitionItem> withdrawList = new LinkedHashMap<>();
    String finalItem;

    Area area = Area.fromPolygon(new WorldTile[]{new WorldTile(3154, 3636, 0), new WorldTile(3154, 3627, 0),
            new WorldTile(3144, 3627, 0), new WorldTile(3144, 3618, 0), new WorldTile(3126, 3618, 0), new WorldTile(3126, 3628, 0),
            new WorldTile(3124, 3628, 0), new WorldTile(3125, 3633, 0), new WorldTile(3125, 3640, 0), new WorldTile(3138, 3640, 0),
            new WorldTile(3139, 3639, 0), new WorldTile(3145, 3639, 0), new WorldTile(3148, 3641, 0),
            new WorldTile(3154, 3641, 0)});


    public BankEventV3() {
        super();

    }

    public static boolean setWithdrawNoted(boolean enable) {
        return enable ? setNoted() : setUnNoted();
    }

    public static boolean setNoted() {
        if (Game.getSetting(115) != 1) {
            Interfaces.get(12, 24).click("Note");
        }
        return Game.getSetting(115) == 1;
    }

    public static boolean setUnNoted() {
        if (Game.getSetting(115) == 1) {
            Optional<Widget> item = Query.widgets().inIndexPath(12).isVisible().textContains("Item").findFirst();
            if(item.isPresent() && item.get().click()) {
                Waiting.waitUntil(5000, () -> Game.getSetting(115) != 1);
            }
        }
        return Game.getSetting(115) != 1;
    }

    public static List<String> expandItemName(String name) {
        ArrayList<String> names = new ArrayList<>();
        Pattern pattern = Pattern.compile("^(.*?)([0-9]+)~([0-9]+)(.*?)$");
        Matcher matcher = pattern.matcher(name);
        if (matcher.find()) {
            String prepend = matcher.group(1), append = matcher.group(4);
            int start = Integer.parseInt(matcher.group(2)), finish = Integer.parseInt(matcher.group(3)),
                    dir = start > finish ? -1 : 1;
            for (int i = start; i * dir <= finish * dir; i += dir) {
                names.add(prepend + i + append);
            }
        } else {
            pattern = Pattern.compile("^(.*?)\\{(.*?)}(.*?)$");
            matcher = pattern.matcher(name);
            if (matcher.find()) {
                String prepend = matcher.group(1), append = matcher.group(3);
                String[] tings = matcher.group(2).split(";");
                for (String t : tings) {
                    names.add((prepend + t + append).trim());
                }
            } else {
                names.add(name);
            }
        }
        return names;
    }

    @Override
    public void step() {
        if(!isPendingOperation()) {
            if(Bank.isOpen()) {
                if(Bank.close()) {
                    Waiting.waitUntil(5000, () -> !Bank.isOpen());
                }
            } else {
                setComplete();
            }
        }else
        if (GrandExchange.getWindowState() != null) {
            GrandExchange.close();
            Timing.waitCondition(() -> GrandExchange.getWindowState() == null, 4000);
        } else if (!Bank.isOpen()) {
            if(!Bank.isNearby()) {
                GlobalWalking.walkToBank();
            } else {
                if(Bank.open()) {
                    Waiting.waitUntil(5000, () -> Bank.isOpen());
                }
            }
            return;
        }

        if (!Banking.isBankLoaded()) {
            return;
        }

        if (!InventoryEvent.containsOnly(arryOfItemsToWithdraw())) {
            General.println("Banking unreqired items");
            Banking.depositAllExcept(arryOfItemsToWithdraw());
            Timing.waitCondition(() -> InventoryEvent.containsOnly(arryOfItemsToWithdraw()), 6000);
        }
        for (Map.Entry<String, RequisitionItem> withdrawList : withdrawList.entrySet()) {
            if(!Bank.isOpen()) {
                break;
            }
            RequisitionItem reqItem = withdrawList.getValue();
            String itemName = reqItem.getName();
            int amount = reqItem.getQty();
            Supplier<Boolean> itemCondition = reqItem.getCondition();
            boolean noted = reqItem.getNoted();
            finalItem = "";

            if (itemName.contains("~")) {
                List<String> expandedItem = expandItemName(itemName);
                for (String item : expandedItem) {
                    if (contains(item)) {
                        finalItem = item;
                        break;
                    }
                }
            } else {
                finalItem = itemName;
            }
            if (finalItem.equals("")) {
                continue;
            }
            RSItem finalRsItem = InventoryEvent.getInventoryItem(finalItem);
            RSItemDefinition finalItemDefinition = null;
            if (finalRsItem != null) {
                finalItemDefinition = finalRsItem.getDefinition();
            }
            if (InventoryEvent.contains(finalRsItem) && finalItemDefinition != null && !finalItemDefinition.isNoted() && noted) {
                General.println("Depositing item: " + finalItem + " we need noted");
                Banking.deposit(InventoryEvent.getCount(itemName), itemName);
                Timing.waitCondition(() -> !InventoryEvent.contains(finalRsItem), 2000);
            } else if (InventoryEvent.contains(finalRsItem) && finalItemDefinition != null && finalItemDefinition.isNoted() && !noted) {
                debug("Depositing noted item as we need it unoted");
                Banking.deposit(Inventory.getCount(finalItemDefinition.getID()), itemName);
                Waiting.waitUntil(5000, () -> !InventoryEvent.contains(itemName));
            } else if ((!bankCacheHashMap.containsKey(finalItem) || !contains(finalItem)) && itemCondition.get()) {
                General.println("Stopping we dont have item '" + finalItem + "' in bank");
                updateCache();
                setComplete();
            } else if (!InventoryEvent.contains(finalItem)) {
                if (!itemCondition.get()) {
                    continue;
                }
                boolean setStatus = setWithdrawNoted(noted);
                if (!setStatus) {
                    continue;
                } else if (noted) {
                    General.println("Withdrawing noted: " + finalItem);
                }
                while (!InventoryEvent.contains(finalItem) && Bank.isOpen()) {
                    if (Banking.withdraw(amount, finalItem)) {
                        Timing.waitCondition(() -> InventoryEvent.contains(finalItem), 2000);
                    }
                }
            } else if (InventoryEvent.contains(finalItem) && finalRsItem != null) {
                boolean isStackable = finalItemDefinition != null && finalItemDefinition.isStackable();
                int itemCount = isStackable ? finalRsItem.getStack() : InventoryEvent.getCount(finalItem);
                boolean shouldWithdraw = itemCount < amount;
                // boolean setWithdrawStatus = setWithdrawNoted(noted);
                BooleanSupplier bankWaitCondition = isStackable ? () -> InventoryEvent.getStackedCount(finalItem) == amount : () -> InventoryEvent.getCount(finalItem) == amount;
                debug("Trying to withdraw: " + finalItem);
                if(InventoryEvent.contains(finalItem) && Inventory.getCount(finalItem) < amount) {
                    if (shouldWithdraw && Banking.withdraw(amount - itemCount, finalItem)) {
                        debug("Succesfully withdrew: " + finalItem + (amount - itemCount));
                        Timing.waitCondition(bankWaitCondition, 2000);
                    }
                }
            }
        }
        setComplete();
    }

    public void setWithdrawList(String itemName, int amount, boolean noted, Supplier<Boolean> condition) {
        if (withdrawList.containsKey(itemName)) {
            if (withdrawList.get(itemName).getQty() != amount) {
                withdrawList.replace(itemName, new RequisitionItem(itemName, amount, noted, condition));
            }
        } else {
            withdrawList.put(itemName, new RequisitionItem(itemName, amount, noted, condition));
        }
    }

    public BankEventV3 addReq(String itemName, int amount) {
        setWithdrawList(itemName, amount, false, () -> true);
        return this;
    }

    public BankEventV3 addReq(String itemName, int amount, Supplier<Boolean> condition) {
        setWithdrawList(itemName, amount, false, condition);
        return this;
    }

    public BankEventV3 addReq(String itemName, int amount, boolean noted) {
        setWithdrawList(itemName, amount, noted, () -> true);
        return this;
    }

    public BankEventV3 addReq(String itemName, int amount, boolean noted, Supplier<Boolean> condition) {
        setWithdrawList(itemName, amount, noted, condition);
        return this;
    }

    public String[] arryOfItemsToWithdraw() {
        String[] items = withdrawList.keySet().toArray(new String[0]);
        List<String> allItems = new ArrayList<>();
        for(String other : items) {
            if(other.contains("~")) {
                for(String item : expandItemName(other)) {
                    if(org.tribot.script.sdk.Inventory.contains(item)) {
                        allItems.add(item);
                        break;
                    }
                }
            } else {
                allItems.add(other);
            }
        }
        return allItems.toArray(new String[0]);
    }

    public boolean isPendingOperation() {
        for (Map.Entry<String, RequisitionItem> withdrawList : withdrawList.entrySet()) {
            String itemName = withdrawList.getKey();
            finalItem = "";
            Supplier<Boolean> cond = withdrawList.getValue().getCondition();
            boolean noted = withdrawList.getValue().getNoted();
            if (cond.get()) {
                if (itemName.contains("~")) {
                    List<String> expandedItem = expandItemName(itemName);
                    if (expandedItem.size() > 0) {
                        for (String item : expandedItem) {
                            Optional<InventoryItem> inventoryItem = MethodsV2.getInventoryItem(item);
                            if (inventoryItem.isPresent()) {
                                finalItem = item;
                                break;
                            } else {
                                finalItem = item;
                            }
                        }
                    }
                } else {
                    finalItem = itemName;
                }
                if (finalItem.equals("")) {
                    continue;
                }
                Optional<InventoryItem> item = Query.inventory().nameEquals(finalItem).isNotNoted().stream().findAny();
                Optional<InventoryItem> itemNoted = Query.inventory().nameEquals(finalItem).isNoted().stream().findAny();
                if (noted) {
                    if (itemNoted.isEmpty()) {
                        General.println("BankEvent is Pending Operation: We dont have: " + finalItem + " noted");
                        return true;
                    }
                } else if (item.isEmpty()) {
                    General.println("BankEvent is Pending Operation: We dont have: " + finalItem);
                    return true;
                }
            }
        }
        return false;
    }

    public RSItem getBankItem(String... itemNames) {
        RSItem[] items = Banking.find(itemNames);
        for (RSItem item : items) {
            if (item != null) {
                return item;
            }
        }
        return null;
    }

    public int getCount(String... itemNames) {
        int amount = 0;
        if (getBankItem(itemNames) != null) {
            amount = getBankItem(itemNames).getStack();
        }
        return amount;
    }

    public boolean contains(String itemName) {
        Optional<Item> item = Query.bank().nameEquals(itemName).findFirst();
        return item.isPresent();
    }

    public void bankEquipment() {
        if (!Banking.isBankScreenOpen()) {
            if (!Banking.isInBank()) {
                GlobalWalking.walkToBank();
            } else if (Banking.openBank()) {
                Timing.waitCondition(Banking::isBankScreenOpen, 2000);
            }
        } else {
            if (Banking.depositEquipment()) {
                Timing.waitCondition(() -> Equipment.getItems().length == 0, 2000);
            }
        }
    }

    public boolean openBank() {
        if (!Banking.isBankScreenOpen()) {
            if(GrandExchange.getWindowState() == null) {
                if (Bank.isNearby()) {
                    if (Bank.open()) {
                        Waiting.waitUntil(5000, Bank::isOpen);
                    }
                } else {
                    if (GlobalWalking.walkToBank()) {
                        Waiting.waitUntil(10000, Bank::isNearby);
                    }
                }
            } else {
                if(GrandExchange.close()) {
                    Waiting.waitUntil(5000, () -> GrandExchange.getWindowState() == null);
                }
            }
        } else {
            depositAll();
            General.sleep(1000);
            updateCache();
            org.tribot.script.sdk.cache.BankCache.update();
            return true;
        }
        return false;
    }

    public void depositAll() {
        if (Banking.isBankScreenOpen()) {
            if (getAll().length > 0) {
                Banking.depositAll();
            }
        }
    }

    public boolean needCache() {
        return bankCacheHashMap.size() <= 0;
    }

    public void updateItem(String itemName, int id, int amount) {
        int old;
        if (bankCacheHashMap.containsKey(itemName)) {
            old = bankCacheHashMap.get(itemName).getQty();
            bankCacheHashMap.replace(itemName, new BankCache(itemName, id, amount+old));
        } else {
            bankCacheHashMap.put(itemName, new BankCache(itemName, id, amount));
        }
    }

    public void updateCache() {
        if (!Banking.isBankScreenOpen() && Banking.isBankLoaded()) {
            General.println("Bank is not open cannot continue");
        } else {
            bankCacheHashMap = new HashMap<>();
            RSItem[] bankCache = Banking.getAll();
            for (RSItem rsItem : bankCache) {
                updateItem(rsItem.getDefinition().getName(), rsItem.getDefinition().getID(), rsItem.getStack());
            }
            RSItem[] inventCache = Inventory.getAll();
            for (RSItem item : inventCache) {
                int qty;
                if (item.getDefinition().isStackable()) {
                    qty = item.getStack();
                } else {
                    qty = InventoryEvent.getItemCount(item.getDefinition().getName());
                }
                updateItem(item.getDefinition().getName(), item.getDefinition().getID(), qty);
            }
            RSItem[] equipment = Equipment.getItems();
            for (RSItem rsItem : equipment) {
                updateItem(rsItem.getDefinition().getName(), rsItem.getDefinition().getID(), rsItem.getStack());
            }
        }
    }
}
package scripts.events;

import org.tribot.api.input.Mouse;
import org.tribot.api2007.Game;
import org.tribot.api2007.types.RSPlayer;
import org.tribot.api2007.types.RSTile;
import org.tribot.script.sdk.*;
import org.tribot.script.sdk.query.Query;
import org.tribot.script.sdk.types.LocalTile;
import org.tribot.script.sdk.types.WorldTile;
import scripts.core.botevent.BotEventv2;
import scripts.core.utilities.DrinkPrayerPotion;
import scripts.core.utilities.HealEventV2;

import java.awt.*;
import java.util.Arrays;

import static org.tribot.api2007.Player.getRSPlayer;
import static org.tribot.script.sdk.Log.debug;

public class TestAttack extends BotEventv2 {

    RSTile firstTile = new RSTile(2864, 5360, 2);
    RSTile secondTile = new RSTile(2864, 5369, 2);
    RSTile thirdTile = new RSTile(2876, 5366, 2);
    RSTile fourthTile = new RSTile(2876, 5357, 2);
    RSTile fifthTile = new RSTile(2876, 5351, 2);
    RSTile sixthTile = new RSTile(2864, 5351, 2);

    boolean first = true, second, third, fourth, fifth, six;

    HealEventV2 healEvent;
    DrinkPrayerPotion drinkPrayerPotion;

    DrinkStaminaEvent drinkStaminaEvent;



    public TestAttack() {
        healEvent = new HealEventV2().foodType("Anglerfish");
        drinkPrayerPotion = new DrinkPrayerPotion();
        drinkStaminaEvent = new DrinkStaminaEvent();
        debug("Testing Dentists");
    }

    private void resetLoop() {
        first = false;
        second = false;
        third = false;
        fourth = false;
        fifth = false;
        six = false;
    }

    @Override
    public void step() {
        if (Query.npcs()
                .nameContains("General Graardor")
                .isReachable()
                .isAny()) {
            if (!first) {
                if (clickTile(firstTile, secondTile)) {
                    if (isTrueTile(firstTile)) {
                        attack();
                        first = true;
                    }
                }
            } else if (!second) {
                if (clickTile(secondTile, thirdTile)) {
                    if (isTrueTile(secondTile)) {
                        attack();
                        second = true;
                    }
                }
            } else if (!third) {
                if (clickTile(thirdTile, fourthTile)) {
                    if (isTrueTile(thirdTile)) {
                        attack();
                        third = true;
                    }
                }
            } else if (!fourth) {
                Prayer.enableQuickPrayer();
                if (clickTile(fourthTile, fifthTile)) {
                    if (isTrueTile(fourthTile)) {
                        attack();
                        fourth = true;
                    }
                }
            } else if (!fifth) {
                if (clickTileRotate(fifthTile, sixthTile, 0)) {
                    if (isTrueTile(fifthTile)) {
                        attack();
                        fifth = true;
                    }
                }
            } else if (!six) {
                if (clickTileRotate(sixthTile, firstTile, 270)) {
                    if (isTrueTile(sixthTile)) {
                        attack();
                        six = true;
                        resetLoop();
                    }
                }
            } else {
                resetLoop();
            }
        } else {
            debug("We killed it");
            setComplete();
        }
    }

    //Returns True Tile position
    public static RSTile getServerPosition() {
        RSPlayer player = getRSPlayer();
        if (player == null) {
            return new RSTile(-1, -1, -1);
        }
        return new RSTile(
                player.getWalkingQueueX()[0] + Game.getBaseX(),
                player.getWalkingQueueY()[0] + Game.getBaseY(),
                Game.getPlane(),
                RSTile.TYPES.WORLD
        );
    }

    //Returns if True Tile position is same as RSTile tile
    public static boolean isTrueTile(RSTile tile) {
        return getServerPosition().equals(tile);
    }

    //Returns true if trueTile is equal to worldTile
   // private boolean isTrueTile(WorldTile worldTile) {
      //  return getTrueTile().getTile().equals(worldTile);
    //}

    /*private boolean needToAttack(WorldTile tile, int distance) {
        return tile.distance() <= distance;
    }*/

    private boolean attack() {
        if (hoveringRightAction("Attack")) {
            Mouse.click(1);
            return true;
        }
        return Query.npcs()
                .nameEquals("General Graardor")
                .isReachable()
                .findFirst()
                .map(npc -> npc.interact("Attack") )//add intrupt if banados in attack range
                .orElse(false);
    }

    private boolean hoverNpc() {
        return Query.npcs()
                .nameEquals("General Graardor")
                .isReachable()
                .findFirst()
                .map(npc -> npc.hoverMenu("Attack"))
                .orElse(false);
    }

    private boolean clickTileRotate(RSTile tile, RSTile nextTile, int rotation) {
        if (!hoveringRightAction("Attack")) {
            if (tile.isOnScreen()) {
                if (tile.click("Walk here")) {
                    Camera.setRotation(rotation);
                    Camera.setAngle(100);
                    healEvent.execute();
                    healEvent.reset();
                    drinkPrayerPotion.execute();
                    drinkPrayerPotion.reset();
                    drinkStaminaEvent.execute();
                    drinkStaminaEvent.reset();
                    if (hoverNpc()) {
                        return nextTile.isOnScreen();
                    }
                }
            } else {
                if (tile.click()) {
                    healEvent.execute();
                    healEvent.reset();
                    drinkPrayerPotion.execute();
                    drinkPrayerPotion.reset();
                    drinkStaminaEvent.execute();
                    drinkStaminaEvent.reset();
                    if (hoverNpc()) {
                        return nextTile.isOnScreen();
                    }
                }
            }
        }
        return hoveringRightAction("Attack");
    }

    private boolean clickTile(RSTile tile, RSTile nextTile) {
        if (!hoveringRightAction("Attack")) {
            if (tile.isOnScreen()) {
                if (tile.click("Walk here")) {
                    healEvent.execute();
                    healEvent.reset();
                    drinkPrayerPotion.execute();
                    drinkPrayerPotion.reset();
                    drinkStaminaEvent.execute();
                    drinkStaminaEvent.reset();
                    if (hoverNpc()) {
                        return nextTile.isOnScreen();
                    }
                }
            } else {
                WorldTile tile1 = new WorldTile(tile.getX(), tile.getY(), tile.getPlane());
                if (tile1.clickOnMinimap()) {
                    healEvent.execute();
                    healEvent.reset();
                    drinkPrayerPotion.execute();
                    drinkPrayerPotion.reset();
                    drinkStaminaEvent.execute();
                    drinkStaminaEvent.reset();
                    if (hoverNpc()) {
                        return nextTile.isOnScreen();
                    }
                }
            }
        }
        return hoveringRightAction("Attack");
    }

    public boolean hoveringRightAction(String action) {
        if (!ChooseOption.isOpen()) {
            return false;
        }
        return Arrays.stream(org.tribot.api2007.ChooseOption.getMenuNodes())
                .filter(node -> node.getAction().trim().equalsIgnoreCase(action))
                .findFirst()
                .map(node -> {
                    Rectangle box = node.getArea();
                    if (box == null) {
                        return false;
                    }
                    return box.contains(Mouse.getPos());
                }).orElse(false);

    }


}

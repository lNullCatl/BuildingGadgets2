package com.direwolf20.buildinggadgets2.common.capabilities;

import com.direwolf20.buildinggadgets2.setup.BG2DataComponents;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public class EnergyStorageItemstack implements EnergyHandler {
    protected final ItemStack itemStack;
    protected final int capacity;
    private final EnergyJournal journal = new EnergyJournal();

    public EnergyStorageItemstack(int capacity, ItemStack itemStack) {
        this.capacity = capacity;
        this.itemStack = itemStack;
    }

    public void setEnergy(int energy) {
        itemStack.set(BG2DataComponents.FORGE_ENERGY, Math.max(0, Math.min(capacity, energy)));
    }

    @Override
    public long getAmountAsLong() {
        return itemStack.getOrDefault(BG2DataComponents.FORGE_ENERGY, 0);
    }

    @Override
    public long getCapacityAsLong() {
        return capacity;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        int stored = itemStack.getOrDefault(BG2DataComponents.FORGE_ENERGY, 0);
        int inserted = Math.min(capacity - stored, amount);
        if (inserted > 0) {
            journal.updateSnapshots(transaction);
            itemStack.set(BG2DataComponents.FORGE_ENERGY, stored + inserted);
            return inserted;
        }
        return 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        int stored = itemStack.getOrDefault(BG2DataComponents.FORGE_ENERGY, 0);
        int extracted = Math.min(stored, amount);
        if (extracted > 0) {
            journal.updateSnapshots(transaction);
            itemStack.set(BG2DataComponents.FORGE_ENERGY, stored - extracted);
            return extracted;
        }
        return 0;
    }

    private class EnergyJournal extends SnapshotJournal<Integer> {
        @Override
        protected Integer createSnapshot() {
            return itemStack.getOrDefault(BG2DataComponents.FORGE_ENERGY, 0);
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            itemStack.set(BG2DataComponents.FORGE_ENERGY, snapshot);
        }
    }
}

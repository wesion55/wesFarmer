package xyz.geik.wesfarmer.sell;

import org.bukkit.entity.Player;
import xyz.geik.wesfarmer.config.MessageService;
import xyz.geik.wesfarmer.economy.EconomyService;
import xyz.geik.wesfarmer.product.FarmerProduct;
import xyz.geik.wesfarmer.product.ProductManager;
import xyz.geik.wesfarmer.storage.FarmerHistoryRecord;
import xyz.geik.wesfarmer.storage.FarmerHistoryRepository;
import xyz.geik.wesfarmer.storage.FarmerProductStateRepository;
import xyz.geik.wesfarmer.storage.FarmerRecord;
import xyz.geik.wesfarmer.storage.FarmerSettingsRepository;
import xyz.geik.wesfarmer.storage.FarmerStockRepository;

import java.util.ArrayList;
import java.util.List;

import static xyz.geik.wesfarmer.config.MessageService.Placeholder.of;

/**
 * Çiftçi stoğundaki ürünlerin satışını yönetir.
 * Vergi hesabı yapılır, net tutar Vault üzerinden oyuncuya ödenir,
 * işlem geçmişe kaydedilir.
 */
public final class SellService {

    private final EconomyService economyService;
    private final MessageService messageService;
    private final ProductManager productManager;
    private final FarmerStockRepository stockRepository;
    private final FarmerHistoryRepository historyRepository;
    private final FarmerSettingsRepository settingsRepository;
    private final FarmerProductStateRepository stateRepository;

    public SellService(EconomyService economyService,
                        MessageService messageService,
                        ProductManager productManager,
                        FarmerStockRepository stockRepository,
                        FarmerHistoryRepository historyRepository,
                        FarmerSettingsRepository settingsRepository,
                        FarmerProductStateRepository stateRepository) {
        this.economyService     = economyService;
        this.messageService     = messageService;
        this.productManager     = productManager;
        this.stockRepository    = stockRepository;
        this.historyRepository  = historyRepository;
        this.settingsRepository = settingsRepository;
        this.stateRepository    = stateRepository;
    }

    /**
     * Çiftçideki tüm satışa açık ürünleri satar.
     *
     * @return toplam net kazanç
     */
    public double sellAll(Player player, FarmerRecord record) {
        if (!economyService.isAvailable()) {
            messageService.send(player, "economy-not-found");
            return 0;
        }
        if (settingsRepository.get(record.id()).sellLocked()) {
            messageService.send(player, "sell-locked");
            return 0;
        }

        double totalNet = 0;
        List<String> soldLines = new ArrayList<>();

        for (FarmerProduct product : productManager.getAll()) {
            // Hem global sellEnabled hem farmer-bazlı state kontrolü
            if (!stateRepository.getSellEnabled(record.id(), product.material(), product.sellEnabled())) continue;

            int stock = stockRepository.getStock(record.id(), product.material());
            if (stock <= 0) continue;

            double net = product.netEarnings(stock);
            totalNet += net;

            economyService.deposit(player, net);
            stockRepository.clearStock(record.id(), product.material());
            historyRepository.insert(record.id(),
                    FarmerHistoryRecord.Type.SELL,
                    product.material(), stock, net);

            soldLines.add(product.displayName() + " x" + stock
                    + " → &a+" + String.format("%.2f", net) + "$");
        }

        if (totalNet <= 0) {
            messageService.send(player, "sell-nothing-to-sell");
            return 0;
        }

        messageService.send(player, "sell-all-success",
                of("%earnings%", String.format("%.2f", totalNet)));
        return totalNet;
    }

    /**
     * Belirli bir üründen {@code amount} kadar satar.
     *
     * @return net kazanç
     */
    public double sellProduct(Player player, FarmerRecord record,
                               FarmerProduct product, int amount) {
        if (!economyService.isAvailable()) {
            messageService.send(player, "economy-not-found");
            return 0;
        }
        if (settingsRepository.get(record.id()).sellLocked()) {
            messageService.send(player, "sell-locked");
            return 0;
        }

        // Ürün bazlı satış durumu — farmer bazlı DB'den, varsayılan products.yml
        boolean sellEnabled = stateRepository.getSellEnabled(record.id(), product.material(), product.sellEnabled());
        if (!sellEnabled) {
            messageService.send(player, "sell-product-disabled");
            return 0;
        }

        int stock = stockRepository.getStock(record.id(), product.material());
        if (stock <= 0) {
            messageService.send(player, "sell-no-stock");
            return 0;
        }

        int toSell = Math.min(amount, stock);
        double net = product.netEarnings(toSell);

        economyService.deposit(player, net);
        stockRepository.addStock(record.id(), product.material(), -toSell);
        historyRepository.insert(record.id(),
                FarmerHistoryRecord.Type.SELL,
                product.material(), toSell, net);

        messageService.send(player, "sell-success",
                of("%amount%",   String.valueOf(toSell)),
                of("%item%",     product.displayName()),
                of("%earnings%", String.format("%.2f", net)));
        return net;
    }
}

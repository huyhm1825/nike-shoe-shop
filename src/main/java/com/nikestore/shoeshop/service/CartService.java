package com.nikestore.shoeshop.service;

import com.nikestore.shoeshop.dto.CartItemView;
import com.nikestore.shoeshop.entity.Product;
import org.springframework.stereotype.Service;
import jakarta.servlet.http.HttpSession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
public class CartService {

    private static final String CART_KEY = "CART";

    @SuppressWarnings("unchecked")
    private Map<String, CartItemView> cart(HttpSession session) {
        Object obj = session.getAttribute(CART_KEY);
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, CartItemView>) map;
        }
        Map<String, CartItemView> newCart = new LinkedHashMap<>();
        session.setAttribute(CART_KEY, newCart);
        return newCart;
    }

    private String cartKey(Long productId, String size) {
        return productId + "_" + normalizeSize(size);
    }

    public void add(HttpSession session, Product product, int quantity, String size) {
        Map<String, CartItemView> cart = cart(session);
        String normalizedSize = normalizeSize(size);
        String key = cartKey(product.getId(), normalizedSize);
        CartItemView item = cart.get(key);
        int requestedQty = Math.max(quantity, 1);
        int stock = product.getStock() != null ? product.getStock() : 0;
        if (stock <= 0) {
            return;
        }

        if (item == null) {
            // New item: cap at stock limit
            int finalQty = Math.min(requestedQty, stock);
            if (finalQty < 1) {
                return;
            }
            item = new CartItemView(
                    product.getId(),
                    product.getName(),
                    product.getSlug(),
                    product.getImageUrl(),
                    product.effectivePrice(),
                    finalQty,
                    normalizedSize
            );
            cart.put(key, item);
        } else {
            // Existing item with same size: add quantity but cap at stock limit
            int newQty = item.getQuantity() + requestedQty;
            int finalQty = Math.min(newQty, stock);
            item.setQuantity(finalQty);
        }
    }

    public void update(HttpSession session, Long productId, int quantity, String oldSize, String newSize, Product product) {
        Map<String, CartItemView> cart = cart(session);
        String normalizedOldSize = normalizeSize(oldSize);
        String normalizedNewSize = normalizeSize(newSize);
        String oldKey = cartKey(productId, normalizedOldSize);
        String newKey = cartKey(productId, normalizedNewSize);
        CartItemView item = cart.get(oldKey);
        if (item != null && product != null) {
            int requestedQty = Math.max(quantity, 1);
            int stock = product.getStock() != null ? product.getStock() : 0;
            if (stock <= 0) {
                cart.remove(oldKey);
                cart.remove(newKey);
                return;
            }
            int finalQty = Math.min(requestedQty, stock);

            if (Objects.equals(normalizedOldSize, normalizedNewSize)) {
                // Same size, just update quantity
                item.setQuantity(finalQty);
            } else {
                // Size changed: remove from old, add to new (or merge if new exists)
                CartItemView existingNew = cart.get(newKey);
                if (existingNew != null) {
                    // New size already exists: merge quantities
                    existingNew.setQuantity(Math.min(existingNew.getQuantity() + finalQty, stock));
                } else {
                    // Create new entry with new size
                    CartItemView newItem = new CartItemView(
                            product.getId(),
                            product.getName(),
                            product.getSlug(),
                            product.getImageUrl(),
                            product.effectivePrice(),
                            finalQty,
                            normalizedNewSize
                    );
                    cart.put(newKey, newItem);
                }
                // Remove old entry
                cart.remove(oldKey);
            }
        }
    }

    public void remove(HttpSession session, Long productId, String size) {
        cart(session).remove(cartKey(productId, size));
    }

    public void remove(HttpSession session, Long productId) {
        // Remove all sizes of this product
        cart(session).entrySet().removeIf(entry -> entry.getValue().getProductId().equals(productId));
    }

    public void clear(HttpSession session) {
        cart(session).clear();
    }

    public Map<String, CartItemView> items(HttpSession session) {
        return cart(session);
    }

    public double total(HttpSession session) {
        return cart(session).values().stream().mapToDouble(CartItemView::getSubtotal).sum();
    }

    public int count(HttpSession session) {
        return cart(session).values().stream().mapToInt(CartItemView::getQuantity).sum();
    }

    private String normalizeSize(String size) {
        return size == null || size.isBlank() ? "42" : size.trim();
    }
}

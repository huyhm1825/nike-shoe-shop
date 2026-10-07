package com.nikestore.shoeshop.service;

import com.nikestore.shoeshop.dto.CartItemView;
import com.nikestore.shoeshop.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;

class CartServiceTest {

    private final CartService cartService = new CartService();

    @Test
    void addShouldNotStoreItemWhenProductOutOfStock() {
        MockHttpSession session = new MockHttpSession();
        Product product = sampleProduct(1L, 0);

        cartService.add(session, product, 2, "42");

        assertTrue(cartService.items(session).isEmpty());
    }

    @Test
    void addShouldNormalizeSizeAndCapQuantityByStock() {
        MockHttpSession session = new MockHttpSession();
        Product product = sampleProduct(2L, 3);

        cartService.add(session, product, 10, " 43 ");

        assertEquals(1, cartService.items(session).size());
        CartItemView item = cartService.items(session).values().iterator().next();
        assertEquals("43", item.getSize());
        assertEquals(3, item.getQuantity());
    }

    @Test
    void updateShouldRemoveItemWhenStockBecomesZero() {
        MockHttpSession session = new MockHttpSession();
        Product inStock = sampleProduct(3L, 2);
        cartService.add(session, inStock, 1, "42");

        Product outOfStock = sampleProduct(3L, 0);
        cartService.update(session, 3L, 1, "42", "42", outOfStock);

        assertTrue(cartService.items(session).isEmpty());
    }

    private Product sampleProduct(Long id, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName("Sample");
        product.setSlug("sample");
        product.setPrice(1000000d);
        product.setSalePrice(900000d);
        product.setImageUrl("/img/sample.svg");
        product.setStock(stock);
        return product;
    }
}

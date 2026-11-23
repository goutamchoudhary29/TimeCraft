import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import {
  api,
  clearAuthSession,
  getStoredUser,
  saveAuthSession,
  STORAGE_KEYS,
} from '../api/client';

const StoreContext = createContext(null);

export function StoreProvider({ children }) {
  const [user, setUser] = useState(() => getStoredUser());
  const [cart, setCart] = useState({ items: [], subtotal: 0, itemCount: 0 });
  const [wishlistIds, setWishlistIds] = useState([]);
  const [compareItems, setCompareItems] = useState(() => {
    try {
      return JSON.parse(localStorage.getItem(STORAGE_KEYS.COMPARE) || '[]');
    } catch {
      return [];
    }
  });
  const [bagOpen, setBagOpen] = useState(false);
  const [searchOpen, setSearchOpen] = useState(false);
  const [compareOpen, setCompareOpen] = useState(false);
  const [quickViewSlug, setQuickViewSlug] = useState(null);
  const [toasts, setToasts] = useState([]);

  const notify = useCallback((message, type = 'info') => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 3200);
  }, []);

  const refreshCart = useCallback(async () => {
    try {
      const data = await api('/cart');
      if (data) setCart(data);
    } catch {
      // Guest cart may not exist yet
    }
  }, []);

  const refreshWishlist = useCallback(async () => {
    if (!getStoredUser()) {
      setWishlistIds([]);
      return;
    }
    try {
      const data = await api('/wishlist');
      const items = data?.items || [];
      setWishlistIds(items.map((i) => i.productId));
    } catch {
      setWishlistIds([]);
    }
  }, []);

  useEffect(() => {
    refreshCart();
    refreshWishlist();
  }, [refreshCart, refreshWishlist]);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEYS.COMPARE, JSON.stringify(compareItems));
  }, [compareItems]);

  const loginSuccess = useCallback(
    async (authResponse) => {
      saveAuthSession(authResponse);
      setUser(authResponse.user);
      await Promise.all([refreshCart(), refreshWishlist()]);
      notify(`Welcome back, ${authResponse.user?.firstName || 'Collector'}`);
    },
    [notify, refreshCart, refreshWishlist]
  );

  const logout = useCallback(async () => {
    try {
      await api('/auth/logout', { method: 'POST' });
    } catch {
      // ignore
    }
    clearAuthSession();
    setUser(null);
    setWishlistIds([]);
    await refreshCart();
    notify('Signed out successfully');
  }, [notify, refreshCart]);

  const addToBag = useCallback(
    async (variantId, quantity = 1) => {
      try {
        const updated = await api('/cart/items', {
          method: 'POST',
          body: { variantId, quantity },
        });
        if (updated) setCart(updated);
        setBagOpen(true);
        notify('Added timepiece to your bag');
      } catch (err) {
        notify(err.message || 'Could not add to bag', 'error');
      }
    },
    [notify]
  );

  const updateBagQty = useCallback(
    async (itemId, quantity) => {
      try {
        const updated =
          quantity <= 0
            ? await api(`/cart/items/${itemId}`, { method: 'DELETE' })
            : await api(`/cart/items/${itemId}`, { method: 'PUT', body: { quantity } });
        if (updated) setCart(updated);
      } catch (err) {
        notify(err.message || 'Could not update bag', 'error');
      }
    },
    [notify]
  );

  const toggleWishlist = useCallback(
    async (productId) => {
      if (!user) {
        notify('Please sign in to save watches to your wishlist', 'error');
        return false;
      }
      const isSaved = wishlistIds.includes(productId);
      try {
        if (isSaved) {
          await api(`/wishlist/items/${productId}`, { method: 'DELETE' });
          setWishlistIds((prev) => prev.filter((id) => id !== productId));
          notify('Removed from wishlist');
        } else {
          await api(`/wishlist/items/${productId}`, { method: 'POST' });
          setWishlistIds((prev) => [...prev, productId]);
          notify('Saved to your wishlist');
        }
        return true;
      } catch (err) {
        notify(err.message || 'Wishlist update failed', 'error');
        return false;
      }
    },
    [notify, user, wishlistIds]
  );

  const toggleCompare = useCallback(
    (watch) => {
      setCompareItems((prev) => {
        const exists = prev.some((w) => w.id === watch.id);
        if (exists) {
          notify(`Removed ${watch.name} from comparison`);
          return prev.filter((w) => w.id !== watch.id);
        }
        if (prev.length >= 4) {
          notify('Compare up to 4 timepieces at once', 'error');
          return prev;
        }
        notify(`Added ${watch.name} to compare`);
        return [...prev, watch];
      });
    },
    [notify]
  );

  const clearCompare = useCallback(() => setCompareItems([]), []);

  return (
    <StoreContext.Provider
      value={{
        user,
        setUser,
        cart,
        setCart,
        refreshCart,
        wishlistIds,
        refreshWishlist,
        compareItems,
        toggleCompare,
        clearCompare,
        bagOpen,
        setBagOpen,
        searchOpen,
        setSearchOpen,
        compareOpen,
        setCompareOpen,
        quickViewSlug,
        setQuickViewSlug,
        toasts,
        notify,
        loginSuccess,
        logout,
        addToBag,
        updateBagQty,
        toggleWishlist,
      }}
    >
      {children}
    </StoreContext.Provider>
  );
}

export function useStore() {
  return useContext(StoreContext);
}

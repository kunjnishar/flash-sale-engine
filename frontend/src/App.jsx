import { useCallback, useEffect, useState } from 'react';
import Navbar from './components/Navbar';
import ProductCard from './components/ProductCard';
import BenchmarkPanel from './components/BenchmarkPanel';
import MetricsDashboard from './components/MetricsDashboard';
import Toast from './components/Toast';
import { fetchProducts, purchase, extractErrorMessage } from './services/api';

let toastCounter = 0;

export default function App() {
  const [products, setProducts] = useState([]);
  const [isLoadingProducts, setIsLoadingProducts] = useState(true);
  const [toasts, setToasts] = useState([]);
  const [metrics, setMetrics] = useState(null);

  const loadProducts = useCallback(async () => {
    try {
      const data = await fetchProducts();
      setProducts(data);
    } catch (err) {
      pushToast('error', 'Could not load products. Is the backend running on port 8080?');
    } finally {
      setIsLoadingProducts(false);
    }
  }, []);

  useEffect(() => {
    loadProducts();
  }, [loadProducts]);

  function pushToast(type, message) {
    const id = ++toastCounter;
    setToasts((prev) => [...prev, { id, type, message }]);
    setTimeout(() => dismissToast(id), 4000);
  }

  function dismissToast(id) {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }

  async function handleQuickBuy(productId) {
    try {
      const order = await purchase('optimistic', {
        userId: Math.floor(Math.random() * 1_000_000),
        productId,
        quantity: 1,
      });
      pushToast('success', `Order ${order.orderTrackingNumber} confirmed for ${order.productName}.`);
      await loadProducts();
      return order;
    } catch (err) {
      pushToast('error', extractErrorMessage(err));
      throw err;
    }
  }

  function handleBenchmarkComplete(result) {
    setMetrics(result);
    pushToast(
      'info',
      `${result.strategyLabel} run finished: ${result.successfulOrders}/${result.totalRequests} orders confirmed.`
    );
    loadProducts();
  }

  function handleBenchmarkError(message) {
    pushToast('error', message);
  }

  function handleResetSuccess(resetProducts) {
    setProducts(resetProducts);
    setMetrics(null);
    pushToast('success', 'Inventory reset to default stock levels. Redis locks flushed.');
  }

  function handleResetError(message) {
    pushToast('error', message);
  }

  return (
    <div className="min-h-screen bg-void">
      <Navbar onResetSuccess={handleResetSuccess} onResetError={handleResetError} />

      <main className="max-w-6xl mx-auto px-6 py-10 flex flex-col gap-12">
        <section>
          <h1 className="font-display font-semibold text-ink text-xl mb-1">Storefront</h1>
          <p className="text-mute text-sm mb-6">
            Buy instantly, or run a concurrency test below to stress-test the inventory engine.
          </p>

          {isLoadingProducts ? (
            <p className="text-mute text-sm font-mono">Loading products…</p>
          ) : products.length === 0 ? (
            <p className="text-mute text-sm">No products found. Make sure the backend has seeded data.</p>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
              {products.map((product) => (
                <ProductCard
                  key={product.id}
                  product={product}
                  onPurchaseSuccess={handleQuickBuy}
                />
              ))}
            </div>
          )}
        </section>

        <section className="flex flex-col gap-6">
          <h2 className="font-display font-semibold text-ink text-xl">Concurrency lab</h2>

          {products.length > 0 && (
            <BenchmarkPanel
              products={products}
              onRunComplete={handleBenchmarkComplete}
              onError={handleBenchmarkError}
            />
          )}

          <MetricsDashboard metrics={metrics} />
        </section>
      </main>

      <div className="fixed bottom-6 right-6 flex flex-col gap-3 z-50">
        {toasts.map((toast) => (
          <Toast key={toast.id} toast={toast} onDismiss={dismissToast} />
        ))}
      </div>
    </div>
  );
}
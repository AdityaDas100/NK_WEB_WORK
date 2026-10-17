"use client";

import { useMemo, useState } from "react";

import { Search } from "@/components/icons/Icons";
import { ProductCard } from "@/components/site/ProductCard";
import { inputIdle } from "@/components/ui/styles";
import type { Product, TherapeuticArea } from "@/lib/products";

type AreaFilter = TherapeuticArea | "All";

/**
 * Client-side catalogue browser.
 *
 * The product list is passed in from the server component, so the markup for the initial (unfiltered)
 * state is fully server-rendered: it is visible before hydration and never re-renders into a
 * different layout. Filtering only ever swaps the cards inside a grid whose track sizes are defined
 * by CSS, and the result counter keeps a reserved line height, so typing in the search box cannot
 * move the layout.
 */
export function ProductCatalog({ products }: { products: readonly Product[] }) {
  const [query, setQuery] = useState("");
  const [area, setArea] = useState<AreaFilter>("All");

  const areas = useMemo<readonly AreaFilter[]>(() => {
    const unique = new Set(products.map((product) => product.therapeuticArea));
    return ["All", ...unique];
  }, [products]);

  const filtered = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return products.filter((product) => {
      const matchesArea = area === "All" || product.therapeuticArea === area;
      if (!matchesArea) {
        return false;
      }
      if (needle.length === 0) {
        return true;
      }
      return (
        product.name.toLowerCase().includes(needle) ||
        product.genericName.toLowerCase().includes(needle) ||
        product.therapeuticArea.toLowerCase().includes(needle) ||
        product.indication.toLowerCase().includes(needle)
      );
    });
  }, [products, query, area]);

  return (
    <div className="min-w-0">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="relative w-full min-w-0 lg:max-w-sm">
          <label htmlFor="product-search" className="sr-only">
            Search the product catalogue
          </label>
          <span className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-400">
            <Search />
          </span>
          <input
            id="product-search"
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search by name, INN or indication"
            autoComplete="off"
            className={`${inputIdle} pl-11`}
          />
        </div>

        <ul className="flex flex-wrap gap-2" aria-label="Filter by therapeutic area">
          {areas.map((option) => {
            const isActive = option === area;
            return (
              <li key={option}>
                <button
                  type="button"
                  aria-pressed={isActive}
                  onClick={() => setArea(option)}
                  className={`inline-flex h-9 items-center rounded-full border px-3.5 text-sm font-medium transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-brand-700 ${
                    isActive
                      ? "border-brand-700 bg-brand-700 text-white"
                      : "border-ink-200 bg-white text-ink-600 hover:border-brand-300 hover:bg-brand-50 hover:text-ink-900"
                  }`}
                >
                  {option}
                </button>
              </li>
            );
          })}
        </ul>
      </div>

      {/* Reserved height: switching between "showing n" and the empty state cannot shift the grid. */}
      <p className="mt-4 min-h-5 text-sm tabular-nums text-ink-500" aria-live="polite" role="status">
        {filtered.length === 0
          ? "No products match your search."
          : `Showing ${filtered.length} of ${products.length} products.`}
      </p>

      {filtered.length === 0 ? (
        <div className="mt-2 flex min-h-52 flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-ink-200 bg-ink-50/50 px-6 py-10 text-center">
          <p className="text-sm font-medium text-ink-700">Nothing matched “{query.trim()}”.</p>
          <button
            type="button"
            onClick={() => {
              setQuery("");
              setArea("All");
            }}
            className="text-sm font-semibold text-brand-700 underline underline-offset-4 hover:text-brand-800"
          >
            Reset filters
          </button>
        </div>
      ) : (
        <ul className="mt-2 grid grid-cols-1 items-stretch gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {filtered.map((product) => (
            <li key={product.id} className="min-w-0">
              <ProductCard product={product} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

package toolo

import (
	"fmt"
	"sort"
	"sync"
)

// KSPDelegator manages and routes queries to multiple KnowledgeSearchProviders.
type KSPDelegator struct {
	providers   []KSPRegistration
	providersMu sync.RWMutex
}

// NewKSPDelegator creates a new KSPDelegator.
func NewKSPDelegator() *KSPDelegator {
	return &KSPDelegator{
		providers: make([]KSPRegistration, 0),
	}
}

// RegisterProvider adds a KnowledgeSearchProvider to the delegator.
// Providers are sorted by priority (lower number means higher priority).
func (d *KSPDelegator) RegisterProvider(provider KnowledgeSearchProvider, priority int) error {
	d.providersMu.Lock()
	defer d.providersMu.Unlock()

	name := provider.GetName()
	for _, p := range d.providers {
		if p.Name == name {
			return fmt.Errorf("provider with name '%s' already registered", name)
		}
	}

	d.providers = append(d.providers, KSPRegistration{
		Name:     name,
		Provider: provider,
		Priority: priority,
	})

	// Sort providers by priority
	sort.Slice(d.providers, func(i, j int) bool {
		return d.providers[i].Priority < d.providers[j].Priority
	})

	return nil
}

// UnregisterProvider removes a KnowledgeSearchProvider from the delegator.
func (d *KSPDelegator) UnregisterProvider(name string) error {
	d.providersMu.Lock()
	defer d.providersMu.Unlock()

	found := false
	newProviders := make([]KSPRegistration, 0, len(d.providers))
	for _, p := range d.providers {
		if p.Name == name {
			found = true
		} else {
			newProviders = append(newProviders, p)
		}
	}

	if !found {
		return fmt.Errorf("provider with name '%s' not found", name)
	}
	d.providers = newProviders
	return nil
}

// ListProviders returns the names of all registered providers, ordered by priority.
func (d *KSPDelegator) ListProviders() []string {
	d.providersMu.RLock()
	defer d.providersMu.RUnlock()

	names := make([]string, len(d.providers))
	for i, p := range d.providers {
		names[i] = p.Name
	}
	return names
}

// DelegateQuery sends a query to the highest priority provider that can handle it.
// Currently, it sends to all providers and aggregates results, prioritizing by KSP priority.
// More sophisticated routing logic can be added here based on query type or capabilities.
func (d *KSPDelegator) DelegateQuery(query KSPQuery) ([]KSPResult, error) {
	d.providersMu.RLock()
	defer d.providersMu.RUnlock()

	if len(d.providers) == 0 {
		return nil, fmt.Errorf("no KSPs registered")
	}

	var allResults []KSPResult
	var errors []error

	// For now, query all providers and collect results.
	// A more advanced implementation might select a specific provider
	// based on query.Context or KSP capabilities.
	for _, reg := range d.providers {
		results, err := reg.Provider.Query(query)
		if err != nil {
			errors = append(errors, fmt.Errorf("provider %s error: %w", reg.Name, err))
			continue // Or handle error differently, e.g., try next provider
		}
		// Potentially augment results with provider name or rank them
		for i := range results {
			if results[i].Data == nil {
				results[i].Data = make(map[string]interface{})
			}
			results[i].Data["ksp_source"] = reg.Name
			results[i].Data["ksp_priority"] = reg.Priority
		}
		allResults = append(allResults, results...)
	}

	// Simple aggregation: sort by relevance (descending)
	// More complex aggregation could consider KSP priority or other factors.
	sort.Slice(allResults, func(i, j int) bool {
		return allResults[i].Relevance > allResults[j].Relevance
	})

	if len(allResults) == 0 && len(errors) > 0 {
		// If no results and there were errors, return a combined error
		// For simplicity, just returning the first error for now
		return nil, fmt.Errorf("query failed across all providers: %w", errors[0])
	}

	// If there were errors but also some results, we might choose to return the results
	// and log the errors, or handle it based on application requirements.

	return allResults, nil
}

// GetProvider retrieves a specific provider by name.
func (d *KSPDelegator) GetProvider(name string) (KnowledgeSearchProvider, error) {
	d.providersMu.RLock()
	defer d.providersMu.RUnlock()

	for _, p := range d.providers {
		if p.Name == name {
			return p.Provider, nil
		}
	}
	return nil, fmt.Errorf("provider with name '%s' not found", name)
}

package toolo

// KSPQuery represents a query to a Knowledge Search Provider.
type KSPQuery struct {
	QueryText string
	Context   map[string]interface{} // Optional context for the query
}

// KSPResult represents a result from a Knowledge Search Provider.
type KSPResult struct {
	ID        string
	Title     string
	Snippet   string
	Relevance float64
	Data      map[string]interface{} // Additional data associated with the result
}

// KnowledgeSearchProvider defines the interface for a KSP.
type KnowledgeSearchProvider interface {
	// Query sends a query to the KSP and returns a list of results.
	Query(query KSPQuery) ([]KSPResult, error)

	// GetName returns the name of the KSP.
	GetName() string

	// GetCapabilities returns a description of the KSP's capabilities.
	// This could include information about the types of queries it supports,
	// the data sources it uses, etc.
	GetCapabilities() map[string]string
}

// KSPRegistration holds information about a registered KSP.
type KSPRegistration struct {
	Name     string
	Provider KnowledgeSearchProvider
	Priority int // Lower numbers mean higher priority
}

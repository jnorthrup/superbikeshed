package cursors

import (
	"fmt"
	"sync"
	"time"

	"app/toolo" // Corrected import path
)

// AttentionFocus represents the current focus of attention.
type AttentionFocus struct {
	ID        string
	Type      string // e.g., "document_section", "user_query", "code_symbol"
	Content   string // Textual content related to the focus
	Metadata  map[string]interface{}
	Timestamp time.Time
}

// AttentionContext holds information about the broader context of attention.
type AttentionContext struct {
	ActiveFocus *AttentionFocus
	History     []*AttentionFocus // Recent attention history
	ActiveKSPs  []string          // Names of KSPs relevant to this context
	UserIntent  string            // Deduced or stated user intent
}

// AttentionService manages the attention focus and leverages KSPs.
type AttentionService struct {
	kspDelegator *toolo.KSPDelegator
	currentFocus *AttentionFocus
	focusHistory []*AttentionFocus
	focusLock    sync.RWMutex
	maxHistory   int
}

// NewAttentionService creates a new AttentionService.
func NewAttentionService(delegator *toolo.KSPDelegator) *AttentionService {
	return &AttentionService{
		kspDelegator: delegator,
		focusHistory: make([]*AttentionFocus, 0),
		maxHistory:   20, // Default max history size
	}
}

// SetFocus updates the current attention focus.
func (s *AttentionService) SetFocus(focusType, content string, metadata map[string]interface{}) *AttentionFocus {
	s.focusLock.Lock()
	defer s.focusLock.Unlock()

	newFocus := &AttentionFocus{
		ID:        fmt.Sprintf("focus-%d", time.Now().UnixNano()), // Simple ID generation
		Type:      focusType,
		Content:   content,
		Metadata:  metadata,
		Timestamp: time.Now(),
	}
	s.currentFocus = newFocus

	// Add to history and maintain max history size
	s.focusHistory = append(s.focusHistory, newFocus)
	if len(s.focusHistory) > s.maxHistory {
		s.focusHistory = s.focusHistory[len(s.focusHistory)-s.maxHistory:]
	}

	return newFocus
}

// GetCurrentFocus returns the current attention focus.
func (s *AttentionService) GetCurrentFocus() *AttentionFocus {
	s.focusLock.RLock()
	defer s.focusLock.RUnlock()
	return s.currentFocus
}

// GetFocusHistory returns the recent history of attention foci.
func (s *AttentionService) GetFocusHistory() []*AttentionFocus {
	s.focusLock.RLock()
	defer s.focusLock.RUnlock()
	// Return a copy to prevent external modification
	historyCopy := make([]*AttentionFocus, len(s.focusHistory))
	copy(historyCopy, s.focusHistory)
	return historyCopy
}

// QueryKSPsForCurrentFocus queries relevant KSPs based on the current attention focus.
// This is a simplified example. A real implementation would have more sophisticated logic
// to determine which KSPs to query and how to formulate the query.
func (s *AttentionService) QueryKSPsForCurrentFocus(additionalContext map[string]interface{}) ([]toolo.KSPResult, error) {
	s.focusLock.RLock()
	currentFocus := s.currentFocus
	s.focusLock.RUnlock()

	if currentFocus == nil {
		return nil, fmt.Errorf("no current attention focus set")
	}

	if s.kspDelegator == nil {
		return nil, fmt.Errorf("KSP delegator not initialized")
	}

	// Formulate a query based on the current focus
	// This could be much more complex, involving NLP, context analysis, etc.
	queryText := currentFocus.Content
	if intent, ok := currentFocus.Metadata["user_intent"]; ok {
		queryText = fmt.Sprintf("%s (Intent: %s)", queryText, intent)
	}

	queryContext := make(map[string]interface{})
	queryContext["focus_type"] = currentFocus.Type
	queryContext["timestamp"] = currentFocus.Timestamp
	for k, v := range currentFocus.Metadata {
		queryContext[k] = v
	}
	for k, v := range additionalContext {
		queryContext[k] = v // Additional context can override focus metadata
	}

	kspQuery := toolo.KSPQuery{
		QueryText: queryText,
		Context:   queryContext,
	}

	// Delegate the query
	// In a more advanced system, we might select specific KSPs based on focus type or metadata
	results, err := s.kspDelegator.DelegateQuery(kspQuery)
	if err != nil {
		return nil, fmt.Errorf("failed to delegate KSP query: %w", err)
	}

	// Further processing of results could happen here, e.g., ranking, filtering
	return results, nil
}

// ClearFocus clears the current attention focus and history.
func (s *AttentionService) ClearFocus() {
	s.focusLock.Lock()
	defer s.focusLock.Unlock()
	s.currentFocus = nil
	s.focusHistory = make([]*AttentionFocus, 0)
}

// SetMaxHistory sets the maximum number of focus items to keep in history.
func (s *AttentionService) SetMaxHistory(max int) {
	if max < 0 {
		max = 0
	}
	s.maxHistory = max
	// Trim history if it's now over the new max
	s.focusLock.Lock()
	defer s.focusLock.Unlock()
	if len(s.focusHistory) > s.maxHistory {
		s.focusHistory = s.focusHistory[len(s.focusHistory)-s.maxHistory:]
	}
}

// Helper function to show an example of how KSP results might be used
// This is illustrative and would be part of a larger system.
func (s *AttentionService) ProcessKSPResultsForAttention(results []toolo.KSPResult) {
	// Example: Log results or update some internal state based on KSP output
	fmt.Printf("AttentionService: Received %d KSP results.\n", len(results))
	for _, res := range results {
		fmt.Printf("  - Result ID: %s, Title: %s, Relevance: %.2f, Source: %v\n",
			res.ID, res.Title, res.Relevance, res.Data["ksp_source"])
		// Further actions could involve updating UI, triggering other processes,
		// or even refining the attention focus itself.
	}
}

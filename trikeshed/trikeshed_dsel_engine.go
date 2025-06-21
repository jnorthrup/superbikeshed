package trikeshed

import (
	"fmt"
	"sync"

	"app/cursors" // Attention service might be used for context
	"app/toolo"   // KSP interfaces and delegator
)

// DSELCommandType represents the type of a DSEL command.
type DSELCommandType string

const (
	CommandDefineAttentionElement DSELCommandType = "DEFINE_ATTENTION_ELEMENT"
	CommandEngageKSP              DSELCommandType = "ENGAGE_KSP"
	CommandSetContext             DSELCommandType = "SET_CONTEXT"
	// Add other DSEL command types as needed
)

// DSELCommand represents a command in the TrikeShed DSEL.
type DSELCommand struct {
	Type   DSELCommandType
	Name   string                 // e.g., name of attention element, KSP to engage
	Params map[string]interface{} // Parameters for the command
	Target string                 // Optional target for the command (e.g., a specific KSP)
}

// AttentionElement defines an element of attention within TrikeShed, potentially linked to KSPs.
type AttentionElement struct {
	Name        string
	Description string
	KSPQueries  []toolo.KSPQuery // Predefined queries or templates
	// Potentially links to Cursors' AttentionFocus types or specific KSPs
	RequiredContext []string // Context keys required to activate/utilize this element
}

// DSELEngine processes DSEL commands and manages TrikeShed state.
type DSELEngine struct {
	kspDelegator     *toolo.KSPDelegator
	attentionService *cursors.AttentionService // Optional, for context awareness
	elements         map[string]AttentionElement
	elementsMu       sync.RWMutex
	currentContext   map[string]interface{}
	contextMu        sync.RWMutex
}

// NewDSELEngine creates a new DSELEngine.
func NewDSELEngine(delegator *toolo.KSPDelegator, attService *cursors.AttentionService) *DSELEngine {
	return &DSELEngine{
		kspDelegator:     delegator,
		attentionService: attService, // Can be nil if not used
		elements:         make(map[string]AttentionElement),
		currentContext:   make(map[string]interface{}),
	}
}

// ExecuteCommand processes a DSEL command.
func (e *DSELEngine) ExecuteCommand(cmd DSELCommand) (interface{}, error) {
	switch cmd.Type {
	case CommandDefineAttentionElement:
		return e.defineAttentionElement(cmd)
	case CommandEngageKSP:
		return e.engageKSP(cmd)
	case CommandSetContext:
		return e.setContext(cmd)
	default:
		return nil, fmt.Errorf("unknown DSEL command type: %s", cmd.Type)
	}
}

func (e *DSELEngine) defineAttentionElement(cmd DSELCommand) (string, error) {
	e.elementsMu.Lock()
	defer e.elementsMu.Unlock()

	name, ok := cmd.Params["name"].(string)
	if !ok || name == "" {
		return "", fmt.Errorf("attention element name is required and must be a string")
	}
	if _, exists := e.elements[name]; exists {
		return "", fmt.Errorf("attention element '%s' already defined", name)
	}

	element := AttentionElement{
		Name: name,
	}
	if desc, ok := cmd.Params["description"].(string); ok {
		element.Description = desc
	}
	// TODO: Parse KSPQueries from cmd.Params if provided
	// TODO: Parse RequiredContext from cmd.Params

	e.elements[name] = element
	return fmt.Sprintf("Attention element '%s' defined", name), nil
}

func (e *DSELEngine) engageKSP(cmd DSELCommand) ([]toolo.KSPResult, error) {
	if e.kspDelegator == nil {
		return nil, fmt.Errorf("KSP delegator not configured")
	}

	queryText, ok := cmd.Params["queryText"].(string)
	if !ok || queryText == "" {
		return nil, fmt.Errorf("queryText is required for ENGAGE_KSP command")
	}

	// Merge DSEL context with command-specific context
	mergedContext := make(map[string]interface{})
	e.contextMu.RLock()
	for k, v := range e.currentContext {
		mergedContext[k] = v
	}
	e.contextMu.RUnlock()

	if cmdCtx, ok := cmd.Params["context"].(map[string]interface{}); ok {
		for k, v := range cmdCtx {
			mergedContext[k] = v
		}
	}

	// Add attention service context if available
	if e.attentionService != nil && e.attentionService.GetCurrentFocus() != nil {
		currentFocus := e.attentionService.GetCurrentFocus()
		mergedContext["current_attention_focus_type"] = currentFocus.Type
		mergedContext["current_attention_focus_content"] = currentFocus.Content
		// Add more from attention service as needed
	}

	kspQuery := toolo.KSPQuery{
		QueryText: queryText,
		Context:   mergedContext,
	}

	// If a specific KSP target is specified, try to use it.
	// Otherwise, use the delegator's default behavior.
	if cmd.Target != "" {
		provider, err := e.kspDelegator.GetProvider(cmd.Target)
		if err != nil {
			return nil, fmt.Errorf("failed to get provider '%s': %w", cmd.Target, err)
		}
		results, err := provider.Query(kspQuery)
		if err != nil {
			return nil, fmt.Errorf("error querying KSP '%s': %w", cmd.Target, err)
		}
		// Augment results with provider name if not already there
		for i := range results {
			if results[i].Data == nil {
				results[i].Data = make(map[string]interface{})
			}
			results[i].Data["ksp_source"] = provider.GetName()
		}
		return results, nil
	}

	return e.kspDelegator.DelegateQuery(kspQuery)
}

func (e *DSELEngine) setContext(cmd DSELCommand) (string, error) {
	e.contextMu.Lock()
	defer e.contextMu.Unlock()

	contextData, ok := cmd.Params["data"].(map[string]interface{})
	if !ok {
		return "", fmt.Errorf("context 'data' is required and must be a map[string]interface{}")
	}

	for key, value := range contextData {
		e.currentContext[key] = value
	}
	return "Context updated", nil
}

// GetAttentionElement retrieves a defined attention element.
func (e *DSELEngine) GetAttentionElement(name string) (AttentionElement, bool) {
	e.elementsMu.RLock()
	defer e.elementsMu.RUnlock()
	element, exists := e.elements[name]
	return element, exists
}

// GetCurrentContext returns a copy of the current DSEL context.
func (e *DSELEngine) GetCurrentContext() map[string]interface{} {
	e.contextMu.RLock()
	defer e.contextMu.RUnlock()
	ctxCopy := make(map[string]interface{})
	for k, v := range e.currentContext {
		ctxCopy[k] = v
	}
	return ctxCopy
}

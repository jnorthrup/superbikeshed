package k2scrikpt

import (
	"app/toolo"
	"encoding/json"
	"fmt"
	"testing"
	"time"
)

// MockNexusHandler is a mock implementation of NexusConnectionHandler for testing.
type MockNexusHandler struct {
	ReceivedMessages []NexusMessage
	HandleError      error
	HandledCount     int
}

func (h *MockNexusHandler) HandleNexusMessage(msg NexusMessage) error {
	h.HandledCount++
	h.ReceivedMessages = append(h.ReceivedMessages, msg)
	return h.HandleError
}

func (h *MockNexusHandler) Reset() {
	h.ReceivedMessages = nil
	h.HandleError = nil
	h.HandledCount = 0
}

func TestNewK2ScrikptNexusConnector(t *testing.T) {
	handler := &MockNexusHandler{}
	endpoint := "test-endpoint"
	connector := NewK2ScrikptNexusConnector(endpoint, handler)

	if connector == nil {
		t.Fatal("NewK2ScrikptNexusConnector returned nil")
	}
	if connector.nexusEndpoint != endpoint {
		t.Errorf("Expected endpoint '%s', got '%s'", endpoint, connector.nexusEndpoint)
	}
	if connector.handler != handler {
		t.Error("Handler not set correctly")
	}
	if connector.isConnected {
		t.Error("Connector should not be connected initially")
	}
}

func TestK2ScrikptNexusConnector_ConnectDisconnect(t *testing.T) {
	connector := NewK2ScrikptNexusConnector("test-ep", nil)

	// Test Connect
	err := connector.Connect()
	if err != nil {
		t.Fatalf("Connect() failed: %v", err)
	}
	if !connector.isConnected {
		t.Error("isConnected should be true after Connect()")
	}

	// Test connecting again (should fail or be idempotent)
	// Current implementation prints, let's assume it should error if already connected
	err = connector.Connect()
	if err == nil {
		t.Error("Connect() should return an error if already connected, got nil")
	}

	// Test Disconnect
	err = connector.Disconnect()
	if err != nil {
		t.Fatalf("Disconnect() failed: %v", err)
	}
	if connector.isConnected {
		t.Error("isConnected should be false after Disconnect()")
	}

	// Test disconnecting again (should fail)
	err = connector.Disconnect()
	if err == nil {
		t.Error("Disconnect() should return an error if not connected, got nil")
	}
}

func TestK2ScrikptNexusConnector_SendMessage_NotConnected(t *testing.T) {
	connector := NewK2ScrikptNexusConnector("test-ep", nil)
	err := connector.SendMessage("testType", "payload", "source", "target")
	if err == nil {
		t.Error("SendMessage() should return an error if not connected, got nil")
	}
}

type TestPayload struct {
	Data string `json:"data"`
	Num  int    `json:"num"`
}

func TestK2ScrikptNexusConnector_SendMessage_Success(t *testing.T) {
	connector := NewK2ScrikptNexusConnector("test-ep", nil)
	connector.Connect() // Assume connect works

	payload := TestPayload{Data: "test data", Num: 123}
	source := "test_source"
	target := "test_target"
	msgType := "TestPayloadType"

	// We can't inspect the "sent" message directly without more complex mocking (e.g. of network layer)
	// For now, we just ensure it doesn't error and runs.
	// The real test would be in an integration test or by inspecting logs/mocked network calls.
	err := connector.SendMessage(msgType, payload, source, target)
	if err != nil {
		t.Fatalf("SendMessage() failed: %v", err)
	}
	// To actually verify, we'd need to capture stdout or have a mockable sending mechanism.
	// The current SendMessage prints to stdout.
}

func TestK2ScrikptNexusConnector_SendMessage_MarshalError(t *testing.T) {
	connector := NewK2ScrikptNexusConnector("test-ep", nil)
	connector.Connect()

	// json.Marshal fails on channel types
	payload := make(chan int)
	err := connector.SendMessage("ErrorType", payload, "s", "t")
	if err == nil {
		t.Error("SendMessage should have failed due to JSON marshal error, but didn't")
	}
}

func TestK2ScrikptNexusConnector_SimulateIncomingMessage_NotConnected(t *testing.T) {
	handler := &MockNexusHandler{}
	connector := NewK2ScrikptNexusConnector("test-ep", handler)
	rawMsg := json.RawMessage(`{"id":"1","type":"Test","payload":{}}`)
	err := connector.SimulateIncomingMessage(rawMsg)
	if err == nil {
		t.Error("SimulateIncomingMessage() should error if not connected, got nil")
	}
}

func TestK2ScrikptNexusConnector_SimulateIncomingMessage_NoHandler(t *testing.T) {
	connector := NewK2ScrikptNexusConnector("test-ep", nil) // Nil handler
	connector.Connect()
	rawMsg := json.RawMessage(`{"id":"1","type":"Test","payload":{}}`)
	err := connector.SimulateIncomingMessage(rawMsg)
	if err == nil {
		t.Error("SimulateIncomingMessage() should error if handler is nil, got nil")
	}
}

func TestK2ScrikptNexusConnector_SimulateIncomingMessage_UnmarshalError(t *testing.T) {
	handler := &MockNexusHandler{}
	connector := NewK2ScrikptNexusConnector("test-ep", handler)
	connector.Connect()

	invalidRawMsg := json.RawMessage(`this is not valid json`)
	err := connector.SimulateIncomingMessage(invalidRawMsg)
	if err == nil {
		t.Error("SimulateIncomingMessage() should error on invalid JSON, got nil")
	}
}

func TestK2ScrikptNexusConnector_SimulateIncomingMessage_HandlerError(t *testing.T) {
	handler := &MockNexusHandler{HandleError: fmt.Errorf("handler failed")}
	connector := NewK2ScrikptNexusConnector("test-ep", handler)
	connector.Connect()

	payload := TestPayload{Data: "sample", Num: 1}
	payloadBytes, _ := json.Marshal(payload)
	msg := NexusMessage{
		ID: "msg1", Type: "TestType", Payload: json.RawMessage(payloadBytes), Source: "s", Target: "t", Timestamp: time.Now().Unix(),
	}
	rawMsg, _ := json.Marshal(msg)

	err := connector.SimulateIncomingMessage(rawMsg)
	if err == nil {
		t.Error("SimulateIncomingMessage() should have returned handler's error, got nil")
	}
	if err.Error() != "handler failed" {
		t.Errorf("Unexpected error message: %s", err.Error())
	}
	if handler.HandledCount != 1 {
		t.Errorf("Handler should have been called once even if it errors, called %d times", handler.HandledCount)
	}
}

func TestK2ScrikptNexusConnector_SimulateIncomingMessage_Success(t *testing.T) {
	handler := &MockNexusHandler{}
	connector := NewK2ScrikptNexusConnector("test-ep", handler)
	connector.Connect()

	expectedPayload := TestPayload{Data: "important data", Num: 42}
	payloadBytes, _ := json.Marshal(expectedPayload)

	expectedMsg := NexusMessage{
		ID:        "msg-success-123",
		Type:      "DataUpdate",
		Payload:   json.RawMessage(payloadBytes),
		Source:    "external_system",
		Target:    "k2scrikpt_module_A",
		Timestamp: time.Now().Unix(),
	}
	rawMsg, _ := json.Marshal(expectedMsg)

	err := connector.SimulateIncomingMessage(rawMsg)
	if err != nil {
		t.Fatalf("SimulateIncomingMessage() failed: %v", err)
	}

	if handler.HandledCount != 1 {
		t.Fatalf("Handler expected to be called once, called %d times", handler.HandledCount)
	}
	if len(handler.ReceivedMessages) != 1 {
		t.Fatalf("Handler should have one message, has %d", len(handler.ReceivedMessages))
	}

	receivedMsg := handler.ReceivedMessages[0]
	if receivedMsg.ID != expectedMsg.ID || receivedMsg.Type != expectedMsg.Type {
		t.Errorf("Received message metadata mismatch. Expected ID %s, Type %s. Got ID %s, Type %s",
			expectedMsg.ID, expectedMsg.Type, receivedMsg.ID, receivedMsg.Type)
	}

	var receivedPayload TestPayload
	err = json.Unmarshal(receivedMsg.Payload, &receivedPayload)
	if err != nil {
		t.Fatalf("Failed to unmarshal received payload: %v", err)
	}
	if receivedPayload != expectedPayload {
		t.Errorf("Received payload mismatch. Expected %+v, got %+v", expectedPayload, receivedPayload)
	}
}

func TestKSPQueryInitiator_SendKSPQueryViaNexus(t *testing.T) {
	// Mock the connector for this test to see what it "sends"
	// For this, we'd ideally have a mock connector or capture SendMessage params.
	// Let's assume SendMessage works and test the initiator's interaction.

	handler := &MockNexusHandler{} // Not strictly needed for sending, but good practice for connector
	connector := NewK2ScrikptNexusConnector("nexus-test", handler)
	connector.Connect() // Mark as connected

	initiatorSourceID := "ksp_initiator_module"
	initiator := NewKSPQueryInitiator(connector, initiatorSourceID)

	query := toolo.KSPQuery{
		QueryText: "find cats",
		Context:   map[string]interface{}{"urgent": true},
	}
	targetKSPService := "NexusKSPRouter"

	// Again, SendMessage prints. We're testing that the initiator calls it correctly.
	// To truly test, we'd need to intercept what SendMessage does.
	// One way: replace connector.SendMessage with a mock function for this test.
	// This is a bit advanced for this setup, so we'll rely on no error.

	originalSendMessage := connector.SendMessage // Not easily mockable without interface change or global var
	// For a more robust test, K2ScrikptNexusConnector.SendMessage would need to be mockable,
	// e.g. if K2ScrikptNexusConnector implemented an interface with SendMessage,
	// or if SendMessage took a sender func as a parameter.

	// For now, just test it doesn't panic and completes.
	// A more thorough test would involve capturing the output of SendMessage or using a test double for the connector.
	err := initiator.SendKSPQueryViaNexus(query, targetKSPService)
	if err != nil {
		t.Fatalf("SendKSPQueryViaNexus failed: %v", err)
	}

	// If we could capture SendMessage arguments, we would verify:
	// - msgType is "KSPQuery"
	// - payload is the marshalled 'query'
	// - source is 'initiatorSourceID'
	// - target is 'targetKSPService'

	// Restore if we had mocked it
	_ = originalSendMessage // To satisfy "declared and not used" if we don't mock
}

func TestGenerateTimestamp(t *testing.T) {
	ts1 := GenerateTimestamp()
	time.Sleep(1 * time.Millisecond) // Ensure time progresses
	ts2 := GenerateTimestamp()
	// Primarily checking it returns something like a Unix timestamp (seconds)
	if ts1 == 0 || ts2 == 0 {
		t.Error("GenerateTimestamp returned zero")
	}
	// Due to system clock granularity, ts1 might equal ts2 if calls are too fast
	// but with a sleep, they should ideally differ or be very close to time.Now().Unix()
	now := time.Now().Unix()
	if ts2 < now-1 || ts2 > now+1 { // Allow a small window for execution time
		t.Errorf("Generated timestamp %d seems too far from current time %d", ts2, now)
	}
}

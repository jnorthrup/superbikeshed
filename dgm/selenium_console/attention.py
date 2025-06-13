"""
Attention Interface implementation for DGM and Nexus integration.
"""

from typing import Dict, Any, List, Optional, Callable
import json
import logging
from dataclasses import dataclass
from datetime import datetime

@dataclass
class AttentionEvent:
    """Represents an attention event in the system."""
    timestamp: datetime
    source: str
    event_type: str
    data: Dict[str, Any]
    priority: int = 0

class AttentionInterface:
    def __init__(self):
        """Initialize the Attention Interface."""
        self.logger = logging.getLogger(__name__)
        self.attention_events: List[AttentionEvent] = []
        self.callbacks: Dict[str, List[Callable]] = {}
        self.max_events = 1000  # Maximum number of events to keep in memory
        
    def register_callback(self, event_type: str, callback: Callable):
        """
        Register a callback for a specific event type.
        
        Args:
            event_type (str): The type of event to register for
            callback (Callable): The callback function to execute
        """
        if event_type not in self.callbacks:
            self.callbacks[event_type] = []
        self.callbacks[event_type].append(callback)
        
    def unregister_callback(self, event_type: str, callback: Callable):
        """
        Unregister a callback for a specific event type.
        
        Args:
            event_type (str): The type of event to unregister from
            callback (Callable): The callback function to remove
        """
        if event_type in self.callbacks:
            self.callbacks[event_type].remove(callback)
            
    def add_event(self, source: str, event_type: str, data: Dict[str, Any], priority: int = 0):
        """
        Add a new attention event.
        
        Args:
            source (str): The source of the event
            event_type (str): The type of event
            data (Dict[str, Any]): The event data
            priority (int): The priority of the event
        """
        event = AttentionEvent(
            timestamp=datetime.now(),
            source=source,
            event_type=event_type,
            data=data,
            priority=priority
        )
        
        # Add event to the list
        self.attention_events.append(event)
        
        # Trim events if we exceed the maximum
        if len(self.attention_events) > self.max_events:
            self.attention_events = self.attention_events[-self.max_events:]
            
        # Notify callbacks
        if event_type in self.callbacks:
            for callback in self.callbacks[event_type]:
                try:
                    callback(event)
                except Exception as e:
                    self.logger.error(f"Error in attention callback: {e}")
                    
    def get_events(self, 
                  source: Optional[str] = None,
                  event_type: Optional[str] = None,
                  min_priority: int = 0,
                  limit: int = 100) -> List[AttentionEvent]:
        """
        Get attention events matching the specified criteria.
        
        Args:
            source (Optional[str]): Filter by source
            event_type (Optional[str]): Filter by event type
            min_priority (int): Minimum priority level
            limit (int): Maximum number of events to return
            
        Returns:
            List[AttentionEvent]: List of matching events
        """
        filtered_events = self.attention_events
        
        if source:
            filtered_events = [e for e in filtered_events if e.source == source]
        if event_type:
            filtered_events = [e for e in filtered_events if e.event_type == event_type]
        if min_priority > 0:
            filtered_events = [e for e in filtered_events if e.priority >= min_priority]
            
        # Sort by timestamp (newest first) and priority
        filtered_events.sort(key=lambda e: (e.timestamp, e.priority), reverse=True)
        
        return filtered_events[:limit]
        
    def clear_events(self):
        """Clear all attention events."""
        self.attention_events.clear()
        
    def get_event_summary(self) -> Dict[str, Any]:
        """
        Get a summary of attention events.
        
        Returns:
            Dict[str, Any]: Summary of attention events
        """
        if not self.attention_events:
            return {
                'total_events': 0,
                'sources': {},
                'event_types': {},
                'priority_levels': {}
            }
            
        sources = {}
        event_types = {}
        priority_levels = {}
        
        for event in self.attention_events:
            # Count sources
            sources[event.source] = sources.get(event.source, 0) + 1
            
            # Count event types
            event_types[event.event_type] = event_types.get(event.event_type, 0) + 1
            
            # Count priority levels
            priority_levels[event.priority] = priority_levels.get(event.priority, 0) + 1
            
        return {
            'total_events': len(self.attention_events),
            'sources': sources,
            'event_types': event_types,
            'priority_levels': priority_levels,
            'latest_event': self.attention_events[-1].timestamp.isoformat()
        } 
"""
Selenium Console implementation for DGM and Nexus integration.
"""

from typing import Optional, Dict, Any, List
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
import json
import logging
import threading
import queue
from .cookie_manager import CookieManager

class SeleniumConsole:
    def __init__(self, headless: bool = False, cookie_dir: str = "cookies"):
        """
        Initialize the Selenium Console.
        
        Args:
            headless (bool): Whether to run the browser in headless mode
            cookie_dir (str): Directory to store cookie files
        """
        self.logger = logging.getLogger(__name__)
        self.message_queue = queue.Queue()
        self.running = False
        self.cookie_manager = CookieManager(cookie_dir)
        
        # Configure Chrome options
        chrome_options = Options()
        if headless:
            chrome_options.add_argument('--headless')
        chrome_options.add_argument('--no-sandbox')
        chrome_options.add_argument('--disable-dev-shm-usage')
        
        # Initialize the WebDriver
        self.driver = webdriver.Chrome(options=chrome_options)
        self.wait = WebDriverWait(self.driver, 10)
        
    def start(self, url: str = "http://localhost:8000", load_cookies: bool = True):
        """
        Start the Selenium console and navigate to the specified URL.
        
        Args:
            url (str): The URL to navigate to
            load_cookies (bool): Whether to load existing cookies
        """
        self.running = True
        self.driver.get(url)
        
        if load_cookies:
            domain = url.split('://')[1].split('/')[0]
            self.cookie_manager.load_cookies(self.driver, domain)
            
        self._start_message_processor()
        
    def stop(self):
        """Stop the Selenium console and clean up resources."""
        self.running = False
        self.driver.quit()
        
    def save_session_cookies(self, domain: str) -> bool:
        """
        Save the current session cookies.
        
        Args:
            domain (str): Domain name for the cookies
            
        Returns:
            bool: True if cookies were saved successfully
        """
        return self.cookie_manager.save_cookies(self.driver, domain)
        
    def get_cookie_for_quic(self, domain: str) -> Optional[Dict[str, str]]:
        """
        Get cookies in a format suitable for the QUIC client.
        
        Args:
            domain (str): Domain name for the cookies
            
        Returns:
            Optional[Dict[str, str]]: Dictionary of cookie name-value pairs
        """
        return self.cookie_manager.export_cookies_for_quic(domain)
        
    def wait_for_authentication(self, cookie_name: str, timeout: int = 300) -> bool:
        """
        Wait for authentication cookie to be present.
        
        Args:
            cookie_name (str): Name of the authentication cookie
            timeout (int): Maximum time to wait in seconds
            
        Returns:
            bool: True if authentication cookie was found
        """
        return self.cookie_manager.wait_for_cookie(self.driver, cookie_name, timeout)
        
    def send_message(self, message: Dict[str, Any]):
        """
        Send a message to be processed by the console.
        
        Args:
            message (Dict[str, Any]): The message to send
        """
        self.message_queue.put(message)
        
    def _start_message_processor(self):
        """Start the message processing thread."""
        def process_messages():
            while self.running:
                try:
                    message = self.message_queue.get(timeout=1)
                    self._handle_message(message)
                except queue.Empty:
                    continue
                except Exception as e:
                    self.logger.error(f"Error processing message: {e}")
                    
        thread = threading.Thread(target=process_messages, daemon=True)
        thread.start()
        
    def _handle_message(self, message: Dict[str, Any]):
        """
        Handle incoming messages.
        
        Args:
            message (Dict[str, Any]): The message to handle
        """
        try:
            message_type = message.get('type')
            if message_type == 'command':
                self._execute_command(message['command'])
            elif message_type == 'status':
                self._update_status(message['status'])
            elif message_type == 'save_cookies':
                domain = message.get('domain')
                if domain:
                    self.save_session_cookies(domain)
            else:
                self.logger.warning(f"Unknown message type: {message_type}")
        except Exception as e:
            self.logger.error(f"Error handling message: {e}")
            
    def _execute_command(self, command: str):
        """
        Execute a command in the console.
        
        Args:
            command (str): The command to execute
        """
        try:
            # Find the command input element
            command_input = self.wait.until(
                EC.presence_of_element_located((By.ID, "command-input"))
            )
            command_input.clear()
            command_input.send_keys(command)
            
            # Find and click the execute button
            execute_button = self.wait.until(
                EC.element_to_be_clickable((By.ID, "execute-button"))
            )
            execute_button.click()
        except Exception as e:
            self.logger.error(f"Error executing command: {e}")
            
    def _update_status(self, status: Dict[str, Any]):
        """
        Update the status display in the console.
        
        Args:
            status (Dict[str, Any]): The status information to display
        """
        try:
            # Find the status element
            status_element = self.wait.until(
                EC.presence_of_element_located((By.ID, "status-display"))
            )
            
            # Update the status display
            status_element.clear()
            status_element.send_keys(json.dumps(status, indent=2))
        except Exception as e:
            self.logger.error(f"Error updating status: {e}") 
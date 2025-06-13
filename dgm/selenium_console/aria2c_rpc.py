"""
aria2c RPC client implementation.
"""

import json
import websocket
import threading
import queue
import time
from typing import Dict, Any, List, Optional, Union, Callable
from dataclasses import dataclass
from datetime import datetime
import logging
import uuid

@dataclass
class DownloadStatus:
    """Represents the status of a download."""
    gid: str
    status: str
    total_length: int
    completed_length: int
    upload_length: int
    bitfield: str
    download_speed: int
    upload_speed: int
    info_hash: str
    num_seeders: int
    seeder: bool
    piece_length: int
    num_pieces: int
    connections: int
    error_code: str
    error_message: str
    followed_by: List[str]
    following: str
    belongs_to: str
    dir: str
    files: List[Dict[str, Any]]
    bittorrent: Dict[str, Any]
    verified_length: int
    verify_integrity_pending: bool

class Aria2cRPC:
    def __init__(self, 
                 host: str = "localhost",
                 port: int = 6800,
                 secret: Optional[str] = None,
                 use_websocket: bool = True):
        """
        Initialize the aria2c RPC client.
        
        Args:
            host (str): RPC server host
            port (int): RPC server port
            secret (Optional[str]): RPC secret token
            use_websocket (bool): Whether to use WebSocket for notifications
        """
        self.logger = logging.getLogger(__name__)
        self.host = host
        self.port = port
        self.secret = secret
        self.use_websocket = use_websocket
        self.ws = None
        self.notification_queue = queue.Queue()
        self.callbacks: Dict[str, List[Callable]] = {}
        self.running = False
        
        if use_websocket:
            self._start_websocket()
            
    def _start_websocket(self):
        """Start the WebSocket connection for notifications."""
        def on_message(ws, message):
            try:
                data = json.loads(message)
                self.notification_queue.put(data)
            except Exception as e:
                self.logger.error(f"Error processing WebSocket message: {e}")
                
        def on_error(ws, error):
            self.logger.error(f"WebSocket error: {error}")
            
        def on_close(ws, close_status_code, close_msg):
            self.logger.info("WebSocket connection closed")
            
        def on_open(ws):
            self.logger.info("WebSocket connection established")
            
        def process_notifications():
            while self.running:
                try:
                    notification = self.notification_queue.get(timeout=1)
                    self._handle_notification(notification)
                except queue.Empty:
                    continue
                except Exception as e:
                    self.logger.error(f"Error processing notification: {e}")
                    
        # Start WebSocket connection
        self.ws = websocket.WebSocketApp(
            f"ws://{self.host}:{self.port}/jsonrpc",
            on_message=on_message,
            on_error=on_error,
            on_close=on_close,
            on_open=on_open
        )
        
        # Start WebSocket thread
        self.running = True
        ws_thread = threading.Thread(target=self.ws.run_forever, daemon=True)
        ws_thread.start()
        
        # Start notification processing thread
        notification_thread = threading.Thread(target=process_notifications, daemon=True)
        notification_thread.start()
        
    def _handle_notification(self, notification: Dict[str, Any]):
        """Handle incoming notifications."""
        try:
            method = notification.get('method')
            if method in self.callbacks:
                for callback in self.callbacks[method]:
                    try:
                        callback(notification.get('params', []))
                    except Exception as e:
                        self.logger.error(f"Error in notification callback: {e}")
        except Exception as e:
            self.logger.error(f"Error handling notification: {e}")
            
    def register_callback(self, method: str, callback: Callable):
        """
        Register a callback for a specific notification method.
        
        Args:
            method (str): Notification method name
            callback (Callable): Callback function
        """
        if method not in self.callbacks:
            self.callbacks[method] = []
        self.callbacks[method].append(callback)
        
    def unregister_callback(self, method: str, callback: Callable):
        """
        Unregister a callback for a specific notification method.
        
        Args:
            method (str): Notification method name
            callback (Callable): Callback function to remove
        """
        if method in self.callbacks:
            self.callbacks[method].remove(callback)
            
    def _rpc_call(self, method: str, params: List[Any] = None) -> Dict[str, Any]:
        """
        Make an RPC call to the aria2c server.
        
        Args:
            method (str): RPC method name
            params (List[Any]): RPC parameters
            
        Returns:
            Dict[str, Any]: RPC response
        """
        try:
            # Prepare request
            request = {
                'jsonrpc': '2.0',
                'id': str(uuid.uuid4()),
                'method': method,
                'params': []
            }
            
            # Add secret if provided
            if self.secret:
                request['params'].append(f"token:{self.secret}")
                
            # Add other parameters
            if params:
                request['params'].extend(params)
                
            # Make request
            response = requests.post(
                f"http://{self.host}:{self.port}/jsonrpc",
                json=request
            )
            response.raise_for_status()
            
            return response.json()
            
        except Exception as e:
            self.logger.error(f"RPC call failed: {e}")
            raise
            
    def add_uri(self, uris: List[str], options: Dict[str, Any] = None) -> str:
        """
        Add a new download.
        
        Args:
            uris (List[str]): List of URIs to download
            options (Dict[str, Any]): Download options
            
        Returns:
            str: GID of the new download
        """
        params = [uris]
        if options:
            params.append(options)
            
        response = self._rpc_call('aria2.addUri', params)
        return response['result']
        
    def add_torrent(self, torrent: bytes, options: Dict[str, Any] = None) -> str:
        """
        Add a new torrent download.
        
        Args:
            torrent (bytes): Torrent file content
            options (Dict[str, Any]): Download options
            
        Returns:
            str: GID of the new download
        """
        params = [torrent.hex()]
        if options:
            params.append(options)
            
        response = self._rpc_call('aria2.addTorrent', params)
        return response['result']
        
    def add_metalink(self, metalink: bytes, options: Dict[str, Any] = None) -> List[str]:
        """
        Add a new metalink download.
        
        Args:
            metalink (bytes): Metalink file content
            options (Dict[str, Any]): Download options
            
        Returns:
            List[str]: List of GIDs of the new downloads
        """
        params = [metalink.hex()]
        if options:
            params.append(options)
            
        response = self._rpc_call('aria2.addMetalink', params)
        return response['result']
        
    def remove(self, gid: str) -> str:
        """
        Remove a download.
        
        Args:
            gid (str): GID of the download to remove
            
        Returns:
            str: GID of the removed download
        """
        response = self._rpc_call('aria2.remove', [gid])
        return response['result']
        
    def force_remove(self, gid: str) -> str:
        """
        Force remove a download.
        
        Args:
            gid (str): GID of the download to force remove
            
        Returns:
            str: GID of the removed download
        """
        response = self._rpc_call('aria2.forceRemove', [gid])
        return response['result']
        
    def pause(self, gid: str) -> str:
        """
        Pause a download.
        
        Args:
            gid (str): GID of the download to pause
            
        Returns:
            str: GID of the paused download
        """
        response = self._rpc_call('aria2.pause', [gid])
        return response['result']
        
    def pause_all(self) -> str:
        """
        Pause all downloads.
        
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.pauseAll')
        return response['result']
        
    def force_pause(self, gid: str) -> str:
        """
        Force pause a download.
        
        Args:
            gid (str): GID of the download to force pause
            
        Returns:
            str: GID of the paused download
        """
        response = self._rpc_call('aria2.forcePause', [gid])
        return response['result']
        
    def force_pause_all(self) -> str:
        """
        Force pause all downloads.
        
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.forcePauseAll')
        return response['result']
        
    def unpause(self, gid: str) -> str:
        """
        Unpause a download.
        
        Args:
            gid (str): GID of the download to unpause
            
        Returns:
            str: GID of the unpaused download
        """
        response = self._rpc_call('aria2.unpause', [gid])
        return response['result']
        
    def unpause_all(self) -> str:
        """
        Unpause all downloads.
        
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.unpauseAll')
        return response['result']
        
    def tell_status(self, gid: str, keys: List[str] = None) -> DownloadStatus:
        """
        Get the status of a download.
        
        Args:
            gid (str): GID of the download
            keys (List[str]): List of keys to retrieve
            
        Returns:
            DownloadStatus: Status of the download
        """
        params = [gid]
        if keys:
            params.append(keys)
            
        response = self._rpc_call('aria2.tellStatus', params)
        return DownloadStatus(**response['result'])
        
    def get_uris(self, gid: str) -> List[Dict[str, Any]]:
        """
        Get the URIs of a download.
        
        Args:
            gid (str): GID of the download
            
        Returns:
            List[Dict[str, Any]]: List of URIs
        """
        response = self._rpc_call('aria2.getUris', [gid])
        return response['result']
        
    def get_files(self, gid: str) -> List[Dict[str, Any]]:
        """
        Get the files of a download.
        
        Args:
            gid (str): GID of the download
            
        Returns:
            List[Dict[str, Any]]: List of files
        """
        response = self._rpc_call('aria2.getFiles', [gid])
        return response['result']
        
    def get_peers(self, gid: str) -> List[Dict[str, Any]]:
        """
        Get the peers of a download.
        
        Args:
            gid (str): GID of the download
            
        Returns:
            List[Dict[str, Any]]: List of peers
        """
        response = self._rpc_call('aria2.getPeers', [gid])
        return response['result']
        
    def get_servers(self, gid: str) -> List[Dict[str, Any]]:
        """
        Get the servers of a download.
        
        Args:
            gid (str): GID of the download
            
        Returns:
            List[Dict[str, Any]]: List of servers
        """
        response = self._rpc_call('aria2.getServers', [gid])
        return response['result']
        
    def tell_active(self, keys: List[str] = None) -> List[DownloadStatus]:
        """
        Get the status of active downloads.
        
        Args:
            keys (List[str]): List of keys to retrieve
            
        Returns:
            List[DownloadStatus]: List of download statuses
        """
        params = []
        if keys:
            params.append(keys)
            
        response = self._rpc_call('aria2.tellActive', params)
        return [DownloadStatus(**status) for status in response['result']]
        
    def tell_waiting(self, offset: int, num: int, keys: List[str] = None) -> List[DownloadStatus]:
        """
        Get the status of waiting downloads.
        
        Args:
            offset (int): Offset
            num (int): Number of downloads to retrieve
            keys (List[str]): List of keys to retrieve
            
        Returns:
            List[DownloadStatus]: List of download statuses
        """
        params = [offset, num]
        if keys:
            params.append(keys)
            
        response = self._rpc_call('aria2.tellWaiting', params)
        return [DownloadStatus(**status) for status in response['result']]
        
    def tell_stopped(self, offset: int, num: int, keys: List[str] = None) -> List[DownloadStatus]:
        """
        Get the status of stopped downloads.
        
        Args:
            offset (int): Offset
            num (int): Number of downloads to retrieve
            keys (List[str]): List of keys to retrieve
            
        Returns:
            List[DownloadStatus]: List of download statuses
        """
        params = [offset, num]
        if keys:
            params.append(keys)
            
        response = self._rpc_call('aria2.tellStopped', params)
        return [DownloadStatus(**status) for status in response['result']]
        
    def change_position(self, gid: str, pos: int, how: str) -> int:
        """
        Change the position of a download in the queue.
        
        Args:
            gid (str): GID of the download
            pos (int): New position
            how (str): How to change the position ("POS_SET", "POS_CUR", "POS_END")
            
        Returns:
            int: New position
        """
        response = self._rpc_call('aria2.changePosition', [gid, pos, how])
        return response['result']
        
    def change_uri(self, gid: str, file_index: int, del_uris: List[str], add_uris: List[str], position: int = None) -> List[int]:
        """
        Change the URIs of a download.
        
        Args:
            gid (str): GID of the download
            file_index (int): File index
            del_uris (List[str]): URIs to delete
            add_uris (List[str]): URIs to add
            position (int): Position to add URIs
            
        Returns:
            List[int]: List of positions
        """
        params = [gid, file_index, del_uris, add_uris]
        if position is not None:
            params.append(position)
            
        response = self._rpc_call('aria2.changeUri', params)
        return response['result']
        
    def get_option(self, gid: str) -> Dict[str, Any]:
        """
        Get the options of a download.
        
        Args:
            gid (str): GID of the download
            
        Returns:
            Dict[str, Any]: Options
        """
        response = self._rpc_call('aria2.getOption', [gid])
        return response['result']
        
    def change_option(self, gid: str, options: Dict[str, Any]) -> str:
        """
        Change the options of a download.
        
        Args:
            gid (str): GID of the download
            options (Dict[str, Any]): New options
            
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.changeOption', [gid, options])
        return response['result']
        
    def get_global_option(self) -> Dict[str, Any]:
        """
        Get the global options.
        
        Returns:
            Dict[str, Any]: Global options
        """
        response = self._rpc_call('aria2.getGlobalOption')
        return response['result']
        
    def change_global_option(self, options: Dict[str, Any]) -> str:
        """
        Change the global options.
        
        Args:
            options (Dict[str, Any]): New global options
            
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.changeGlobalOption', [options])
        return response['result']
        
    def get_global_stat(self) -> Dict[str, Any]:
        """
        Get the global statistics.
        
        Returns:
            Dict[str, Any]: Global statistics
        """
        response = self._rpc_call('aria2.getGlobalStat')
        return response['result']
        
    def purge_download_result(self) -> str:
        """
        Purge completed/error/removed downloads.
        
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.purgeDownloadResult')
        return response['result']
        
    def remove_download_result(self, gid: str) -> str:
        """
        Remove a download result.
        
        Args:
            gid (str): GID of the download
            
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.removeDownloadResult', [gid])
        return response['result']
        
    def get_version(self) -> Dict[str, Any]:
        """
        Get the version information.
        
        Returns:
            Dict[str, Any]: Version information
        """
        response = self._rpc_call('aria2.getVersion')
        return response['result']
        
    def get_session_info(self) -> Dict[str, Any]:
        """
        Get the session information.
        
        Returns:
            Dict[str, Any]: Session information
        """
        response = self._rpc_call('aria2.getSessionInfo')
        return response['result']
        
    def shutdown(self) -> str:
        """
        Shutdown aria2c.
        
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.shutdown')
        return response['result']
        
    def force_shutdown(self) -> str:
        """
        Force shutdown aria2c.
        
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.forceShutdown')
        return response['result']
        
    def save_session(self) -> str:
        """
        Save the current session.
        
        Returns:
            str: "OK"
        """
        response = self._rpc_call('aria2.saveSession')
        return response['result'] 
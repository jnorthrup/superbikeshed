# IPFS (Kubo) Docker Setup Guide

This document provides instructions to set up an IPFS (Kubo) node using Docker. Kubo is the reference implementation of IPFS.

## 1. Docker Image

We will use the official IPFS Kubo Docker image: `ipfs/kubo:latest`.

## 2. Data Persistence

IPFS requires persistent storage for its data store and optionally for staging files. It's highly recommended to use Docker volumes for this:

*   `/data/ipfs`: Stores the main IPFS repository, including all pinned data and node configuration.
    *   Example: `-v ipfs_data:/data/ipfs`
*   `/export`: An optional staging directory. You can copy files here before adding them to IPFS from within the container.
    *   Example: `-v ipfs_export:/export`

Using named volumes (`ipfs_data`, `ipfs_export`) makes managing the data easier.

## 3. Port Mapping

IPFS uses several ports for different services:

*   **API Server (Port 5001):** For interacting with the IPFS daemon programmatically.
    *   Map to host: `-p 5001:5001/tcp`
*   **Gateway (Port 8080 in container):** For accessing IPFS content via HTTP. It's good practice to map this to a different host port if `8080` is already in use.
    *   Map to host: `-p 8082:8080/tcp` (using host port `8082` for this guide)
*   **Swarm Connections (Port 4001):** For connecting with other IPFS peers.
    *   Map to host: `-p 4001:4001/tcp`
    *   Optionally also map UDP: `-p 4001:4001/udp` (for QUIC transport, improving connectivity)

## 4. Initialization

On the first run with new volumes, the IPFS daemon will initialize its repository in the `/data/ipfs` volume. This process is automatic.

## 5. Docker Run Command Example

Here is a complete `docker run` command to start an IPFS Kubo container:

```bash
docker run -d \
    --name my_ipfs_node \
    -v ipfs_data:/data/ipfs \
    -v ipfs_export:/export \
    -p 5001:5001/tcp \
    -p 8082:8080/tcp \
    -p 4001:4001/tcp \
    -p 4001:4001/udp \
    ipfs/kubo:latest
```

**Command Breakdown:**

*   `-d`: Runs the container in detached mode.
*   `--name my_ipfs_node`: Assigns a name to your container.
*   `-v ipfs_data:/data/ipfs`: Mounts a named volume `ipfs_data` for the IPFS repository.
*   `-v ipfs_export:/export`: Mounts a named volume `ipfs_export` for staging files.
*   `-p 5001:5001/tcp`: Maps the API server port.
*   `-p 8082:8080/tcp`: Maps the Gateway port (host port `8082` to container port `8080`).
*   `-p 4001:4001/tcp`: Maps the Swarm port for TCP connections.
*   `-p 4001:4001/udp`: Maps the Swarm port for UDP connections.
*   `ipfs/kubo:latest`: Specifies the Docker image.

Allow a few moments for the IPFS daemon to start.

## 6. Verification

You can verify that your IPFS node is running and operational through several methods:

*   **Check Node Version (API):**
    ```bash
    curl -X POST http://localhost:5001/api/v0/version
    ```
    This should return a JSON response with the Kubo version details.

*   **Add and Retrieve a Test File:**
    1.  Create a test file on your host machine:
        ```bash
        echo "Hello IPFS from Docker!" > test_ipfs.txt
        ```
    2.  Copy the test file into the container's export directory:
        ```bash
        docker cp test_ipfs.txt my_ipfs_node:/export/test_ipfs.txt
        ```
    3.  Add the file to IPFS from within the container:
        ```bash
        docker exec my_ipfs_node ipfs add /export/test_ipfs.txt
        ```
        This command will output information including the file's Content Identifier (CID). Note down the CID (it will look something like `QmXXXX...`).
    4.  Retrieve the file via the local gateway:
        Replace `<CID>` with the actual CID obtained in the previous step.
        ```bash
        curl http://localhost:8082/ipfs/<CID>
        ```
        This should output "Hello IPFS from Docker!".

*   **Check Container Logs:**
    ```bash
    docker logs my_ipfs_node
    ```
    Look for messages indicating the daemon is ready and online.

## 7. Accessing the Web UI

IPFS Kubo typically provides a Web UI for managing your node and exploring files. It's usually accessible via the API port:

`http://localhost:5001/webui`

You should see the IPFS Web UI in your browser.

## 8. Stopping the Container

To stop your IPFS Kubo container:

```bash
docker stop my_ipfs_node
```

If you also want to remove the container:
```bash
docker rm my_ipfs_node
```
To remove the persistent data volumes (this will delete your IPFS data and staged files):
```bash
docker volume rm ipfs_data
docker volume rm ipfs_export
```

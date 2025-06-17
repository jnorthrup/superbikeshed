# CouchDB Docker Setup Guide

This document provides instructions to set up an Apache CouchDB instance using Docker.

## 1. Docker Image

We will use the official Apache CouchDB Docker image. You can typically find it as `couchdb:latest` or `apache/couchdb:latest` on Docker Hub. For this guide, we'll use `couchdb:latest`.

## 2. Admin Credentials

CouchDB requires an administrator username and password for setup. These are provided to the Docker container via environment variables:

* `COUCHDB_USER`: The desired administrator username.
* `COUCHDB_PASSWORD`: The desired administrator password.

**Important:** For the example below, we use `admin` and `password`. In a real-world deployment, **you must use a strong, unique password.**

## 3. Port Mapping

CouchDB listens on port `5984` by default. We need to map this port from the container to our host machine to access CouchDB.

## 4. Data Persistence (Optional but Recommended)

By default, any data stored in CouchDB will be lost when the Docker container is removed. To persist data, you should mount a Docker volume to the container's data directory, which is `/opt/couchdb/data`.

Example: `-v couchdb_data:/opt/couchdb/data` (this will create a named volume `couchdb_data` if it doesn't exist).

## 5. Docker Run Command Example

Here is a complete `docker run` command to start a CouchDB container:

```bash
docker run -d \
    --name my_couchdb_instance \
    -e COUCHDB_USER=admin \
    -e COUCHDB_PASSWORD=password \
    -p 5984:5984 \
    -v couchdb_data:/opt/couchdb/data \
    couchdb:latest
```

**Command Breakdown:**

* `-d`: Runs the container in detached mode (in the background).
* `--name my_couchdb_instance`: Assigns a recognizable name to your container.
* `-e CO  B_USER=admin`: Sets the admin username.
* `-e COUCHDB_PASSWORD=password`: Sets the admin password ( **remember to change this!** ).
* `-p 5984:5984`: Maps port 5984 on the host to port 5984 in the container.
* `-v couchdb_data:/opt/couchdb/data`: (Optional) Mounts a named volume `couchdb_data` for data persistence. Docker will create this volume if it doesn't already exist.
* `couchdb:latest`: Specifies the Docker image to use.

Allow a minute or two for CouchDB to initialize, especially on the first run.

## 6. Verification

Once the container is running, you can verify that CouchDB is accessible by sending an HTTP request to port `5984` on your localhost:

```bash
curl http://admin:password@localhost:5984/
```

(Note: Replace `admin:password` with your actual credentials if you changed them, or omit them if your CouchDB version allows anonymous access to the root endpoint after setup for a quick check, though providing credentials is more reliable.)

A successful response will be a JSON object similar to this:

```json
{
  "couchdb": "Welcome",
  "version": "3.x.x", // Version might vary
  "git_sha": "xxxxxxxxxx",
  "uuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "features": [ /* ... */ ],
  "vendor": {
    "name": "The Apache Software Foundation"
  }
}
```

If you get a connection refused error, wait a bit longer for CouchDB to start or check the container logs: `docker logs my_couchdb_instance`.

## 7. Accessing Fauxton (Admin UI)

CouchDB includes a web-based administration interface called Fauxton. Once CouchDB is running, you can access Fauxton by navigating your web browser to:

`http://localhost:5984/_utils/`

You will be prompted to log in with the admin credentials you set (`admin`/`password` in this example).

## 8. Stopping the Container

To stop your CouchDB container:

```bash
docker stop my_couchdb_instance
```

If you also want to remove the container (e.g., to start fresh, but be aware this deletes non-persisted data):

```bash
docker rm my_couchdb_instance
```

If you used a named volume for data persistence (`couchdb_data` in the example) and want to remove it as well (this will delete all your CouchDB data):

```bash
docker volume rm couchdb_data
```

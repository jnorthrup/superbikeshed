import docker
import logging
from typing import Optional
from .test_spec import TestSpec

def build_env_images(client: docker.DockerClient, logger: Optional[logging.Logger] = None) -> None:
    """
    Build the environment Docker images.
    
    Args:
        client: Docker client instance
        logger: Optional logger instance
    """
    if logger:
        logger.info("Building environment images...")
    
    # Build base image
    client.images.build(
        path=".",
        tag="dgm_base",
        dockerfile="Dockerfile",
        rm=True,
    )
    
    if logger:
        logger.info("Environment images built successfully")

def build_container(
    test_spec: TestSpec,
    client: docker.DockerClient,
    run_id: str,
    logger: Optional[logging.Logger] = None,
    nocache: bool = False,
    force_rebuild: bool = False,
) -> docker.models.containers.Container:
    """
    Build and return a Docker container for the test.
    
    Args:
        test_spec: TestSpec object containing test configuration
        client: Docker client instance
        run_id: Unique run identifier
        logger: Optional logger instance
        nocache: Whether to disable Docker cache
        force_rebuild: Whether to force rebuild the image
        
    Returns:
        Docker container instance
    """
    if logger:
        logger.info(f"Building container for {test_spec.instance_id}")
    
    # Create container from base image
    container = client.containers.create(
        image="dgm_base",
        name=test_spec.get_instance_container_name(run_id),
        detach=True,
        tty=True,
        working_dir="/testbed",
    )
    
    if logger:
        logger.info(f"Container {container.name} created successfully")
    
    return container

def cleanup_container(
    client: docker.DockerClient,
    container: docker.models.containers.Container,
    logger: Optional[logging.Logger] = None,
) -> None:
    """
    Clean up a Docker container.
    
    Args:
        client: Docker client instance
        container: Container to clean up
        logger: Optional logger instance
    """
    if logger:
        logger.info(f"Cleaning up container {container.name}")
    
    try:
        container.stop()
        container.remove()
    except Exception as e:
        if logger:
            logger.error(f"Error cleaning up container {container.name}: {str(e)}")
        raise 
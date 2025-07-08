Read NVIDIA_RESPONSE.md to understand your mission, then start shell commands to investigate

### Sandboxing with K2SCRIPT_ALLOWED_PATHS

The `nvidia-tasker.main.kts` script now supports sandboxing for file operations. This means that file modifications (insert, replace, delete) can be restricted to a predefined set of directories.

To enable and configure sandboxing, set the `K2SCRIPT_ALLOWED_PATHS` environment variable. This variable should contain a colon-separated list of absolute paths that the script is permitted to access.

**Example:**

To allow the script to modify files only within `/home/user/my_project` and `/tmp/data`, you would set the environment variable as follows:

```bash
export K2SCRIPT_ALLOWED_PATHS="/home/user/my_project:/tmp/data"
```

If `K2SCRIPT_ALLOWED_PATHS` is not set or is empty, the script will have unrestricted file access (no sandboxing).
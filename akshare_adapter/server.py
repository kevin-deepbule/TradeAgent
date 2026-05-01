"""Project-level uvicorn entrypoint for the internal AkShare adapter."""

import uvicorn

from akshare_adapter.config import ADAPTER_HOST, ADAPTER_PORT


def main() -> None:
    """Run the AkShare adapter using its configured host and port."""
    uvicorn.run("akshare_adapter.main:app", host=ADAPTER_HOST, port=ADAPTER_PORT)


if __name__ == "__main__":
    main()


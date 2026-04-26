"""Project-level uvicorn entrypoint with fixed host and port defaults."""

import uvicorn

from backend.config import BACKEND_HOST, BACKEND_PORT, BACKEND_RELOAD


def main() -> None:
    """Run the FastAPI app using project configuration."""
    uvicorn.run(
        "backend.main:app",
        host=BACKEND_HOST,
        port=BACKEND_PORT,
        reload=BACKEND_RELOAD,
    )


if __name__ == "__main__":
    main()

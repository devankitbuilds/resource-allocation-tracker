from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):

    app_name: str = "Resource Allocation Tracker - AI Service"

    debug: bool = False

    ollama_base_url: str = "http://localhost:11434"

    llm_provider: str = "ollama"
    llm_model: str = "mistral:7b-instruct-q4_K_M"

    embedding_model: str = "nomic-embed-text"
    embedding_dimension: int = 768

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
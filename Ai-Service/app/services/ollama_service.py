
from langchain_ollama import ChatOllama, OllamaEmbeddings

from app.core.config import settings


# Chat model connection
chat_model = ChatOllama(
    model=settings.llm_model,
    base_url=settings.ollama_base_url,
    temperature=0
)


# Embedding model connection
embedding_model = OllamaEmbeddings(
    model=settings.embedding_model,
    base_url=settings.ollama_base_url
)


def generate_chat_response(prompt: str) -> str:
    response = chat_model.invoke(prompt)
    return response.content


def generate_embedding(text: str) -> list[float]:
    embedding = embedding_model.embed_query(text)
    return embedding

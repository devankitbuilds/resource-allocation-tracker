
from app.services.ollama_service import (
    generate_chat_response,
    generate_embedding
)


def test_chat_model():
    prompt = "What is resource allocation?"
    response = generate_chat_response(prompt)

    print("\nChat response:")
    print(response)

    assert isinstance(response, str)
    assert len(response) > 0


def test_embedding_model():
    text = "Python developer with FastAPI experience"
    embedding = generate_embedding(text)

    print("\nEmbedding dimensions:")
    print(len(embedding))

    assert len(embedding) == 768

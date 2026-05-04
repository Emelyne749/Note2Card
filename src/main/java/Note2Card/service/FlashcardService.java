package Note2Card.service;

import Note2Card.model.Flashcard;
import Note2Card.model.Note;
import Note2Card.repository.FlashcardRepository;
import Note2Card.repository.NoteRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class FlashcardService {

    private final FlashcardRepository flashcardRepository;
    private final NoteRepository noteRepository;

    @Value("${gemini.api.key}")
    private String apiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com")
            .build();

    public FlashcardService(FlashcardRepository flashcardRepository,
                            NoteRepository noteRepository) {
        this.flashcardRepository = flashcardRepository;
        this.noteRepository = noteRepository;
    }

    public List<Flashcard> generateFlashcards(Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found with id: " + noteId));

        String prompt = """
                You are a study assistant. Generate 5 flashcards from the notes below.
                Return ONLY a JSON array with no extra text, like this:
                [{"question":"...","answer":"..."},...]
                
                Notes:
                """ + note.getContent();

        Map<String, Object> requestBody = Map.of(
                "model", "claude-sonnet-4-20250514",
                "max_tokens", 1000,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        String response = webClient.post()
                .uri("/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(res -> {
                    List<Map<String, Object>> content =
                            (List<Map<String, Object>>) res.get("content");
                    return (String) content.get(0).get("text");
                })
                .block();

        List<Flashcard> flashcards = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, String>> pairs = mapper.readValue(
                    response, new TypeReference<>() {}
            );
            for (Map<String, String> pair : pairs) {
                Flashcard fc = new Flashcard();
                fc.setQuestion(pair.get("question"));
                fc.setAnswer(pair.get("answer"));
                fc.setNote(note);
                flashcards.add(flashcardRepository.save(fc));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse AI response: " + e.getMessage());
        }
        return flashcards;
    }

    public List<Flashcard> getFlashcardsByNote(Long noteId) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new RuntimeException("Note not found with id: " + noteId));
        return flashcardRepository.findByNote(note);
    }
}

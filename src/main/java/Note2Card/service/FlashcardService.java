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

    @Value("${openrouter.api.key}")
    private String apiKey;

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://openrouter.ai/api/v1")
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
                Return ONLY a JSON array with no extra text, no markdown, no backticks, like this:
                [{"question":"...","answer":"..."},...]
                
                Notes:
                """ + note.getContent();

        Map<String, Object> requestBody = Map.of(
                "model", "openrouter/free",
                "max_tokens", 1000,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );
        String response = webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", "http://localhost:8080")
                .header("X-Title", "Note2Card")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .map(res -> {
                    List<Map<String, Object>> choices =
                            (List<Map<String, Object>>) res.get("choices");
                    Map<String, Object> message =
                            (Map<String, Object>) choices.get(0).get("message");
                    return (String) message.get("content");
                })
                .block();

        List<Flashcard> flashcards = new ArrayList<>();
        try {
            String cleaned = response.replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();
            ObjectMapper mapper = new ObjectMapper();
            List<Map<String, String>> pairs = mapper.readValue(
                    cleaned, new TypeReference<>() {}
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
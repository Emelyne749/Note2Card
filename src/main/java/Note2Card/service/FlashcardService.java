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

        int pageCount = note.getContent().split("\n").length / 30;
        int minCards = Math.max(5, pageCount / 2);

        String prompt = """
        You are an expert study assistant and exam coach.
        Generate flashcards from the study notes below.
        
        STRICT RULES:
        - Generate AT LEAST %d flashcards
        - Each flashcard question must be detailed, at least 2-3 lines
        - Each flashcard answer must be detailed, at least 3-4 lines
        - Format questions exactly like real exam questions
        - Focus on the most recurring and important topics
        - If past exam papers are provided below, match their exact question style and format
        - Return ONLY a valid JSON array, no extra text, no markdown:
        [{"question":"...","answer":"..."},...]
        
        STUDY NOTES:
        %s
        
        %s
        """.formatted(
                minCards,
                note.getContent(),
                note.getPastPaper() != null && !note.getPastPaper().isEmpty()
                        ? "PAST EXAM PAPERS (match this format for questions):\n" + note.getPastPaper()
                        : ""
        );

        Map<String, Object> requestBody = Map.of(
                "model", "openrouter/free",
                "max_tokens", 4096,
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
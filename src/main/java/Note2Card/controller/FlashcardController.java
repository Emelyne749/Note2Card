package Note2Card.controller;

import Note2Card.model.Flashcard;
import Note2Card.service.FlashcardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flashcards")
public class FlashcardController {

    private final FlashcardService flashcardService;

    public FlashcardController(FlashcardService flashcardService) {
        this.flashcardService = flashcardService;
    }

    // Generate flashcards from a note using AI
    @PostMapping("/generate/{noteId}")
    public List<Flashcard> generateFlashcards(@PathVariable Long noteId) {
        return flashcardService.generateFlashcards(noteId);
    }

    // Get all flashcards for a specific note
    @GetMapping("/note/{noteId}")
    public List<Flashcard> getFlashcardsByNote(@PathVariable Long noteId) {
        return flashcardService.getFlashcardsByNote(noteId);
    }
}

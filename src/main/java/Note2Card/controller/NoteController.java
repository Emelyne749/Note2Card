package Note2Card.controller;

import Note2Card.model.Note;
import Note2Card.service.NoteService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notes")
@CrossOrigin(origins = "http://localhost:5173")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public Note createNote(@RequestBody Note note) {
        return noteService.saveNote(note);
    }

    @PostMapping("/upload")
    public Note uploadFile(@RequestParam("file") MultipartFile file,
                           @RequestParam("title") String title) throws Exception {
        String content = "";
        String filename = file.getOriginalFilename().toLowerCase();

        if (filename.endsWith(".pdf")) {
            PDDocument doc = Loader.loadPDF(file.getBytes()); // fixed: PDFBox 3.x API
            PDFTextStripper stripper = new PDFTextStripper();
            content = stripper.getText(doc);
            doc.close();
        } else if (filename.endsWith(".docx")) {
            XWPFDocument doc = new XWPFDocument(file.getInputStream());
            content = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .collect(Collectors.joining("\n"));
            doc.close();
        }

        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        return noteService.saveNote(note);
    }

    @PutMapping("/{id}/pastpaper")
    public Note updatePastPaper(@PathVariable Long id,
                                @RequestBody Map<String, String> body) {
        Note note = noteService.getNoteById(id);
        note.setPastPaper(body.get("pastPaper"));
        return noteService.saveNote(note);
    }

    @GetMapping
    public List<Note> getAllNotes() {
        return noteService.getAllNotes();
    }

    @GetMapping("/{id}")
    public Note getNote(@PathVariable Long id) {
        return noteService.getNoteById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteNote(@PathVariable Long id) {
        noteService.deleteNote(id);
        return "Note deleted successfully!";
    }
}
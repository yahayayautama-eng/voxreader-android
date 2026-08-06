package com.example.data.repository

import com.example.domain.repository.Book
import com.example.domain.repository.BookRepository
import com.example.domain.repository.Bookmark
import com.example.domain.repository.Chapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeBookRepository @Inject constructor() : BookRepository {

    private val initialBooks = listOf(
        Book(
            id = "gatsby",
            title = "The Great Gatsby",
            author = "F. Scott Fitzgerald",
            description = "A tragic story of Jay Gatsby, a self-made millionaire, and his pursuit of Daisy Buchanan, a wealthy young woman whom he loved in his youth.",
            genre = "Classic Fiction",
            coverColorHex = "#1A365D",
            totalChapters = 3,
            currentChapterIndex = 0,
            currentPosition = 0,
            isFavorite = true,
            chapters = listOf(
                Chapter(
                    chapterNumber = 1,
                    title = "Chapter I",
                    content = """In my younger and more vulnerable years my father gave me some advice that I've been turning over in my mind ever since.

"Whenever you feel like criticizing any one," he told me, "just remember that all the people in this world haven't had the advantages that you've had."

He didn't say any more, but we've always been unusually communicative in a reserved way, and I understood that he meant a great deal more than that. In consequence, I'm inclined to reserve all judgments, a habit that has opened up many curious natures to me and also made me the victim of not a few veteran bores.

The abnormal mind is quick to detect and attach itself to this quality when it appears in a normal person, and so it came about that in college I was unjustly accused of being a politician, because I was privy to the secret griefs of wild, unknown men.

Most of the confidences were unsought — frequently I have feigned sleep, preoccupation, or a hostile levity when I realized by some unmistakable sign that an intimate revelation was trembling on the horizon.""",
                    estimatedMinutes = 6
                ),
                Chapter(
                    chapterNumber = 2,
                    title = "Chapter II",
                    content = """About half-way between West Egg and New York the motor road hastily joins the railroad and runs beside it for a quarter of a mile, so as to shrink away from a certain desolate area of land.

This is a valley of ashes — a fantastic farm where ashes grow like wheat into ridges and hills and grotesque gardens; where ashes take the forms of houses and chimneys and rising smoke and, finally, with a transcendent effort, of ash-gray men who move dimly and already crumbling through the powdery air.

Occasionally a line of gray cars crawls along an invisible track, gives out a ghastly creak, and comes to rest, and immediately the ash-gray men swarm up with leaden spades and stir up an impenetrable cloud, which screens their obscure operations from your sight.""",
                    estimatedMinutes = 8
                ),
                Chapter(
                    chapterNumber = 3,
                    title = "Chapter III",
                    content = """There was music from my neighbor's house through the summer nights. In his blue gardens men and girls came and went like moths among the whisperings and the champagne and the stars.

At high tide in the afternoon I watched his guests diving from the tower of his raft, or taking the sun on the hot sand of his beach while his two motor-boats slit the waters of the sound, drawing aquaplanes over cataracts of foam.

On week-ends his Rolls-Royce became an omnibus, bearing parties to and from the city between eight o'clock in the morning and long past midnight, while his station wagon scampered like a brisk yellow bug to meet all trains.""",
                    estimatedMinutes = 7
                )
            )
        ),
        Book(
            id = "pride",
            title = "Pride and Prejudice",
            author = "Jane Austen",
            description = "A humorous romantic novel of manners written by Jane Austen, following the turbulent relationship between Elizabeth Bennet and Fitzwilliam Darcy.",
            genre = "Romance / Classic",
            coverColorHex = "#742A2A",
            totalChapters = 2,
            currentChapterIndex = 0,
            currentPosition = 0,
            isFavorite = false,
            chapters = listOf(
                Chapter(
                    chapterNumber = 1,
                    title = "Chapter I",
                    content = """It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.

However little known the feelings or views of such a man may be on his first entering a neighborhood, this truth is so well fixed in the minds of the surrounding families, that he is considered the rightful property of some one or other of their daughters.

"My dear Mr. Bennet," said his lady to him one day, "have you heard that Netherfield Park is let at last?"

Mr. Bennet replied that he had not.

"But it is," returned she; "for Mrs. Long has just been here, and she told me all about it."

Mr. Bennet made no answer.

"Do you not want to know who has taken it?" cried his wife impatiently.

"You want to tell me, and I have no objection to hearing it."

This was invitation enough.""",
                    estimatedMinutes = 5
                ),
                Chapter(
                    chapterNumber = 2,
                    title = "Chapter II",
                    content = """Mr. Bennet was among the earliest of those who waited on Mr. Bingley. He had always intended to visit him, though to the last always assuring his wife that he should not go; and till the evening after the visit was paid she had no knowledge of it.

It was then disclosed in the following manner. Observing his second daughter employed in trimming a hat, he suddenly addressed her with:

"I hope Mr. Bingley will like it, Lizzy."

"We are not in a way to know what Mr. Bingley likes," said her mother resentfully, "since we are not to visit."

"But you forget, Mama," said Elizabeth, "that we shall meet him at the assemblies, and that Mrs. Long has promised to introduce him."

"I do not believe Mrs. Long will do any such thing. She has two nieces of her own. She is a selfish, hypocritical woman, and I have no opinion of her.""",
                    estimatedMinutes = 6
                )
            )
        ),
        Book(
            id = "frankenstein",
            title = "Frankenstein",
            author = "Mary Shelley",
            description = "The story of Victor Frankenstein, a young scientist who creates a sapient creature in an unorthodox scientific experiment.",
            genre = "Gothic Horror / Sci-Fi",
            coverColorHex = "#22543D",
            totalChapters = 2,
            currentChapterIndex = 0,
            currentPosition = 0,
            isFavorite = true,
            chapters = listOf(
                Chapter(
                    chapterNumber = 1,
                    title = "Letter I",
                    content = """To Mrs. Saville, England. St. Petersburgh, Dec. 11th, 17—.

You will rejoice to hear that no disaster has accompanied the commencement of an enterprise which you have regarded with such evil forebodings. I arrived here yesterday, and my first task is to assure my dear sister of my welfare and increasing confidence in the success of my undertaking.

I am already far north of London, and as I walk in the streets of Petersburgh, I feel a cold northern breeze play upon my cheeks, which braces my nerves and fills me with delight. Do you understand this feeling? This breeze, which has travelled from the regions towards which I am advancing, gives me a foretaste of those icy climes.""",
                    estimatedMinutes = 5
                ),
                Chapter(
                    chapterNumber = 2,
                    title = "Chapter 1",
                    content = """I am by birth a Genevese, and my family is one of the most distinguished of that republic. My ancestors had been for many years counsellors and syndics, and my father had served several public offices with honor and reputation. He was respected by all who knew him for his integrity and indefatigable attention to public business.""",
                    estimatedMinutes = 6
                )
            )
        ),
        Book(
            id = "sherlock",
            title = "The Adventures of Sherlock Holmes",
            author = "Arthur Conan Doyle",
            description = "A collection of twelve short stories featuring consulting detective Sherlock Holmes and Dr. John Watson.",
            genre = "Mystery",
            coverColorHex = "#4A5568",
            totalChapters = 2,
            currentChapterIndex = 0,
            currentPosition = 0,
            isFavorite = false,
            chapters = listOf(
                Chapter(
                    chapterNumber = 1,
                    title = "A Scandal in Bohemia",
                    content = """To Sherlock Holmes she is always the woman. I have seldom heard him mention her under any other name. In his eyes she eclipses and predominates the whole of her sex.

It was not that he felt any emotion akin to love for Irene Adler. All emotions, and that one particularly, were abhorrent to his cold, precise but admirably balanced mind. He was, I take it, the most perfect reasoning and observing machine that the world has seen, but as a lover he would have placed himself in a false position.""",
                    estimatedMinutes = 7
                )
            )
        )
    )

    private val booksFlow = MutableStateFlow(initialBooks)

    private val initialBookmarks = listOf(
        Bookmark(
            id = "bm_1",
            bookId = "gatsby",
            chapterIndex = 0,
            chapterTitle = "Chapter I",
            textSnippet = "Whenever you feel like criticizing any one, just remember that all the people in this world haven't had the advantages that you've had.",
            note = "Wise advice from Nick's father."
        ),
        Bookmark(
            id = "bm_2",
            bookId = "pride",
            chapterIndex = 0,
            chapterTitle = "Chapter I",
            textSnippet = "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
            note = "Famous opening line!"
        )
    )

    private val bookmarksFlow = MutableStateFlow(initialBookmarks)

    override fun getBooks(): Flow<List<Book>> = booksFlow

    override fun searchBooks(query: String): Flow<List<Book>> {
        return booksFlow.map { books ->
            if (query.isBlank()) books
            else books.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.author.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true) ||
                        it.genre.contains(query, ignoreCase = true)
            }
        }
    }

    override suspend fun getBookById(id: String): Book? {
        return booksFlow.value.find { it.id == id }
    }

    override suspend fun addBook(book: Book) {
        booksFlow.update { current ->
            current + book
        }
    }

    override suspend fun updateBookProgress(bookId: String, chapterIndex: Int, position: Int) {
        booksFlow.update { current ->
            current.map { book ->
                if (book.id == bookId) {
                    book.copy(
                        currentChapterIndex = chapterIndex,
                        currentPosition = position
                    )
                } else book
            }
        }
    }

    override suspend fun toggleFavorite(bookId: String) {
        booksFlow.update { current ->
            current.map { book ->
                if (book.id == bookId) {
                    book.copy(isFavorite = !book.isFavorite)
                } else book
            }
        }
    }

    override fun getBookmarks(): Flow<List<Bookmark>> = bookmarksFlow

    override suspend fun addBookmark(bookmark: Bookmark) {
        bookmarksFlow.update { current ->
            current + bookmark
        }
    }

    override suspend fun removeBookmark(bookmarkId: String) {
        bookmarksFlow.update { current ->
            current.filterNot { it.id == bookmarkId }
        }
    }
}


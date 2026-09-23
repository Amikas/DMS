package at.fhtw.repository;

import at.fhtw.entity.CollectionDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CollectionDocumentRepository extends JpaRepository<CollectionDocument, Long> {
    List<CollectionDocument> findByCollection_Id(Long collectionId);
    List<CollectionDocument> findByDocument_Id(Long documentId);
    Optional<CollectionDocument> findByCollection_IdAndDocument_Id(Long collectionId, Long documentId);
    boolean existsByCollection_IdAndDocument_Id(Long collectionId, Long documentId);
}

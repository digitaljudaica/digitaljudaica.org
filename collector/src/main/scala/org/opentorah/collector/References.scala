package org.opentorah.collector

import org.opentorah.metadata.Names
import org.opentorah.store.{By, Selector, Store}
import org.opentorah.tei.{Entity, EntityReference}
import org.opentorah.util.Files

// TODO calculate and write into file when local; read from file when not.
final class References(store: Store) {
  private val references: Seq[ReferenceWithSource] = References.references(Seq.empty, store)

  def check(findByRef: String => Option[Entity]): Seq[String] =
    references.flatMap(referenceWithSource =>
      References.checkReference(referenceWithSource.reference, findByRef))

  def noRef: Seq[ReferenceWithSource.FromDocument] =
    References.fromDocument(references)
      .filter(_.reference.ref.isEmpty)
      .sortBy(_.reference.text.toLowerCase)

  def toId(id: String): (Seq[ReferenceWithSource], Seq[ReferenceWithSource]) = {
    val result: Seq[ReferenceWithSource] = references.filter(_.reference.ref.contains(id))
    (References.fromEntity(result), References.fromDocument(result))
  }
}

object References {

  private def references(path: Seq[String], store: Store): Seq[ReferenceWithSource] =
    store.entities.toSeq.flatMap(entities => entities.by.get.stores.flatMap { entityHolder =>
      val entity: Entity = entityHolder.entity
      val entityId: String = entity.id.get
      EntityReference.from(entity.content).map(reference => ReferenceWithSource.FromEntity(
        path = Files.addExtension(path :+ getName(entities.selector) :+ entityId, "html"),
        reference,
        entityId,
        entity.name
      ))
    }) ++
    fromStore(path, store.asInstanceOf[Store.FromElement])

  private def fromStore(path: Seq[String], store: Store.FromElement): Seq[ReferenceWithSource] = {
    val fromElement: Seq[ReferenceWithSource.FromElement] = getReferences(store)
      .map(reference => ReferenceWithSource.FromElement(
        path,
        reference
      ))

    val fromChildren = store match {
      case collection: Collection =>
        val collectionName: String = getName(collection.names)
        val documentsBy: By.FromElement[Document] = collection.by.get
        documentsBy.stores.flatMap { document =>
          val documentPath: Seq[String] = path :+ getName(documentsBy.selector)
          document.by.get.stores.flatMap { teiHolder =>
            val teiHolderName: String = teiHolder.name
            getReferences(teiHolder).map(reference => ReferenceWithSource.FromDocument(
              documentPath :+ teiHolderName,
              reference,
              Hierarchy.fileName(collection),
              collectionName,
              teiHolderName
            ))
          }
        }

      case _ =>
        store.by.toSeq.flatMap(by => by.stores.flatMap(store =>
          fromStore(path ++ Seq(getName(by.selector), getName(store)), store.asInstanceOf[Store.FromElement])))
    }

    fromElement ++ fromChildren
  }

  private def getReferences(store: Store): Seq[EntityReference] = EntityReference.from(
    store.title.map(_.xml).getOrElse(Seq.empty) ++
    store.storeAbstract.map(_.xml).getOrElse(Seq.empty) ++
    store.body.map(_.xml).getOrElse(Seq.empty)
  )

  private def getReferences(teiHolder: TeiHolder): Seq[EntityReference] =
    teiHolder.tei.titleStmt.references /* TODO unfold */ ++ EntityReference.from(
    teiHolder.tei.getAbstract.getOrElse(Seq.empty) ++
    teiHolder.tei.correspDesc.map(_.xml).getOrElse(Seq.empty) ++
    teiHolder.tei.body.xml
  )

  private def getName(selector: Selector): String = getName(selector.names)
  private def getName(store: Store): String = getName(store.names)
  private def getName(names: Names): String = names.name

  private def checkReference(reference: EntityReference,  findByRef: String => Option[Entity]): Option[String] = {
    val name = reference.name
    reference.ref.fold[Option[String]](None) { ref =>
      if (ref.contains(" ")) Some(s"""Value of the ref attribute contains spaces: ref="$ref" """) else {
        findByRef(ref).fold[Option[String]](Some(s"""Unresolvable reference: Name ref="$ref">${name.text}< """)) { named =>
          if (named.entityType == reference.entityType) None
          else Some(s"${reference.entityType} reference to ${named.entityType} ${named.name}: $name [$ref]")
        }
      }
    }
  }

  private def fromEntity(references: Seq[ReferenceWithSource]): Seq[ReferenceWithSource.FromEntity] = references
    .filter(_.isInstanceOf[ReferenceWithSource.FromEntity])
    .map(_.asInstanceOf[ReferenceWithSource.FromEntity])

  private def fromDocument(references: Seq[ReferenceWithSource]): Seq[ReferenceWithSource.FromDocument] = references
    .filter(_.isInstanceOf[ReferenceWithSource.FromDocument])
    .map(_.asInstanceOf[ReferenceWithSource.FromDocument])
}

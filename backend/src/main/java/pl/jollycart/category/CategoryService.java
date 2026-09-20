package pl.jollycart.category;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    public void createInitialCategories() {

        createIfNotExists("Elektronika");
        createIfNotExists("Motoryzacja");
        createIfNotExists("Dom i ogród");
        createIfNotExists("Moda");
        createIfNotExists("Sport i rekreacja");
        createIfNotExists("Praca");
        createIfNotExists("Usługi");
        createIfNotExists("Nauka i edukacja");
        createIfNotExists("Inne");
    }

    private void createIfNotExists(String name) {

        if (!categoryRepository.existsByName(name)) {

            Category category = new Category();
            category.setName(name);

            categoryRepository.save(category);
        }
    }
}
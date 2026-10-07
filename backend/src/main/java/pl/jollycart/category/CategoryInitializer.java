package pl.jollycart.category;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class CategoryInitializer implements CommandLineRunner {

    private final CategoryService categoryService;

    public CategoryInitializer(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Override
    public void run(String... args) {
        categoryService.createInitialCategories();
    }
}
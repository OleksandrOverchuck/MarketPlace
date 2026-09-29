(function () {
  const navbar = document.querySelector(".navbar");
  if (!navbar) return;

  const prefersReducedMotion = window.matchMedia(
    "(prefers-reduced-motion: reduce)",
  ).matches;

  let lastY = window.scrollY;
  let cooldown = false;

  function triggerJiggle() {
    if (prefersReducedMotion) return;
    navbar.classList.remove("jiggle");
    // force reflow so the animation can restart
    void navbar.offsetWidth;
    navbar.classList.add("jiggle");
  }

  navbar.addEventListener("animationend", () => {
    navbar.classList.remove("jiggle");
  });

  window.addEventListener(
    "scroll",
    () => {
      const y = window.scrollY;

      navbar.classList.toggle("scrolled", y > 12);

      const scrollingDown = y > lastY + 4;
      if (scrollingDown && y > 12 && !cooldown) {
        triggerJiggle();
        cooldown = true;
        setTimeout(() => {
          cooldown = false;
        }, 700);
      }

      lastY = y;
    },
    { passive: true },
  );
})();

package nu.rainmaker.testsite.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Adds the layout values (menu, site settings, widget) to every HTML page. */
@ControllerAdvice(annotations = Controller.class)
public class LayoutModelAdvice {

  private final LayoutAttributes layoutAttributes;

  /** Creates the advice. */
  public LayoutModelAdvice(LayoutAttributes layoutAttributes) {
    this.layoutAttributes = layoutAttributes;
  }

  /** Runs before every page controller method. */
  @ModelAttribute
  public void addLayoutAttributes(Model model) {
    model.addAllAttributes(layoutAttributes.asMap());
  }
}

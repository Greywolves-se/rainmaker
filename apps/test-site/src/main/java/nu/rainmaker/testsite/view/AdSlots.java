package nu.rainmaker.testsite.view;

import nu.rainmaker.testsite.content.AdInventory;
import nu.rainmaker.testsite.content.Section;
import org.springframework.stereotype.Component;

/** Creates ad slots with the right ad chosen for the page. */
@Component
public class AdSlots {

  private final AdInventory adInventory;

  /** Creates the factory. */
  public AdSlots(AdInventory adInventory) {
    this.adInventory = adInventory;
  }

  /**
   * Creates a slot.
   *
   * @param format the ad format
   * @param section the page's section (for contextual targeting), or null on the front page
   * @param slotNumber position on the page (0, 1, 2 ...), so slots get different ads
   * @param reserveSpace whether the slot keeps its height while the ad loads
   */
  public AdSlot slot(AdSlot.Format format, Section section, int slotNumber, boolean reserveSpace) {
    return new AdSlot(format, adInventory.pick(section, slotNumber).id(), reserveSpace);
  }
}

package com.github.medavox.kotban

import javafx.scene.Cursor
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region

/**
 * Can be used to add mouse listeners to a {@link Region}
 * and make its width resizable by the user by clicking and dragging the border in the
 * same way as a window.
 * <p>

 * <pre>
 * DragResizer.makeResizable(myAnchorPane)
 * </pre>
 * @author Cannibalsticky (modified from the original DragResizer created by AndyTill)
 */
class DragResizerX(private val region:Region) {

	private var x:Double=0.0
	private var initMinWidth:Boolean=false
	private var draggableZoneX:Boolean=false
	private var dragging:Boolean=false

	fun makeResizable() {
		region.setOnMousePressed { event ->
			// ignore clicks outside of the draggable margin
			if (!isInDraggableZone(event)) { Unit }

			dragging = true

			// make sure that the minimum width is set to the current height once,
			// setting a min width that is smaller than the current width will
			// have no effect
			if (!initMinWidth) {
				region.prefWidth = region.width
				initMinWidth = true
			}
			x = event.x
		}
		region.setOnMouseDragged { event ->
			if (!dragging) { Unit }

			if (draggableZoneX) {
				region.prefWidth +=  (event.x - x)
				x = event.x
			}
		}
		region.setOnMouseMoved { event ->
			if (isInDraggableZone(event) || dragging) {
				if (draggableZoneX) { region.cursor = Cursor.E_RESIZE }

			} else { region.cursor = Cursor.DEFAULT }
		}
		region.setOnMouseReleased {
			dragging = false
			region.cursor = Cursor.DEFAULT
		}
	}

	/**Had to use 2 variables for the control.
	Tried without, had unexpected behaviour (going big was ok, going small nope.)*/
	private fun isInDraggableZone(event:MouseEvent):Boolean {
		draggableZoneX = (event.x > (region.width - RESIZE_MARGIN))
		return draggableZoneX
	}
}
